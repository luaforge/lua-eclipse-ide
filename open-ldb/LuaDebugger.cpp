#define WORKING_ON_PC
#ifdef WORKING_ON_PC
#include "LuaDebugger.h"
#else
#include "hproc/LuaDebugger.h"

#endif

/*
 *  The 2 most commonly used data structures area a map of vectors.
 * This is used for the breakpoint lists and the source lists
 * 
 * map[functionName]->vector[0], vector[1], etc
 * 
 * For breakpoints, the key of the map is the Lua script name. The contents of
 * the vectors contents are just ints representing line numbers
 * map[sort.lua]->vector[44], vector[18], etc
 * map[convert.lua]->vector[5], vector[1], etc
 * 
 * For storing the script line source, the key is the lua script name
 * and the vectors are the actual lines themselves, which have been
 * prefixed with a linenumber. By using a vector, when the "ar" structure
 * is filled in by Lua, the ar->currentline simply indexes this vector to
 * retrieve the line.
 * map[sort.lua]->vector["1 -- first line of script], vector["2 local i = 3"], etc
 * 
 * By maintaining this map of vectors, thread safety is maintained and
 * different Lua execution states of scripts can be separated.
 * 
 * Lua does not have a symbol table per se, that a C++ debugger would use to
 * find and access variables. This means that there is no static analysis. Instead,
 * the ldb works complete by looking at the stacks, the lua state and the variable
 * "lua_Debug* ar", which allows querying for more information via the getinfo
 *   lua_getinfo(L, "lnuS", &ar);
 * More or less information can be filled in by different combinations of flags.
 * So, for example, when the ldb user has entered "continue" and ldb stops at a
 * previously set breakpoint, all stack, variable and stacktrace information is
 * relative to the point in the code where it stopped. This takes the place of
 * static analysis and evaluation that is part of most debuggers. All information
 * is dynamically obtained.
 */

/*
 * Here is the language to parse
   list firstline [lastline]
   break line
   clear line
   display [varname]
   step
   continue
   print stack|trace|breakpoints
   quit
   help
 */

/*
 * These are the minimal substrings necessary for a command
 */
const char listCmd[] = "l";            // list
const char setbreakCmd[] = "b";        // break
const char clrbreakCmd[] = "cl";       // clear
const char displayCmd[] = "d";         // display
const char assignCmd[] = "a";          // assign
const char stepCmd[] = "ste";          // step
const char continueCmd[] = "co";       // continue
const char resumeCmd[] = "resume";     // same as continue, for IDE
const char printCmd[] = "p";           // print
const char quitCmd[] = "q";            // quit
const char helpCmd[] = "help";
const char suspendCmd[] = "su";        // suspend, an IDE command
const char stackForIdeCmd[] = "sta";   // stackforide, an IDE command
const char varForIdeCmd[] = "var";     // get a local var, an IDE command
const char dataForIdeCmd[] = "data";   // get the lua stack values, an IDE command
const char getGlobalsCmd[] = "getglobals"; // get the lua globals, an IDE command

const char allCmds[] = "list firstline [lastline]\nbreak line\nclear line\ndisplay [varname]\nstep\ncontinue\nprint stack|trace|breakpoints\nquit\nhelp\n";

const char invalidCommand[] = "invalid command\n";

/* The debugger can be in the following states.
 * This is actually the key to how execution state is maintained.
 * "step", "continue" and breakpoints use this to give the appearance
 * of control.
 */
#define STEPPING 1
#define CONTINUING 2
int debuggerStates = STEPPING;

/*
 * In order to be thread safe, each script has it's own list of source lines
 * The map uses ar->source as the key (name of the script)
 * The list is a vector of string lines from the original source.
 */
map<string, vector <string> > allScriptsLines;

/*
 * In order to be thread safe, each script has it's own list of breakpoints.
 * The map uases ar->source as the key (name of the script)
 * The list is a vector of the line to break at. In other words,
 * the key/vector combination gives a series of scriptName:lineNumber
 * combinations. Each Lua interpreter has it's own list which makes this
 * thread safe even though there is 1 ldb class.
 */
map<string, vector <int> > allScriptsBreakpoints;

/**
 * Get the breakpoint list for this file (or string containing the script)
 * Each file (or string) has it's own list of breakpoints
 */
vector <int> getBreakPointList(const char* scriptName) {
	return allScriptsBreakpoints[scriptName];
}

debuggerType debugger;

ServerSocket newSocket;
ServerSocket eventSocket;

LuaDebugger::LuaDebugger(){
}

/**
 * Find the list in the map by scriptName and added the breakInfo to the list
 * This allows each thread to have it's own breakpoints.
 */
void LuaDebugger::setBreakPointList(const char* scriptName, int lineNumber) {
	vector <int> list = allScriptsBreakpoints[scriptName];
	list.push_back(lineNumber);
	allScriptsBreakpoints[scriptName] = list;
}

/**
 * Add a line of text from the script.
 * Locate the scriptname in the map and then add to that list.
 */
void LuaDebugger::setLineSourceList(const char* scriptName, char* text) {
	vector <string> list = allScriptsLines[scriptName];
	list.push_back((string)text);
	allScriptsLines[scriptName] = list;
}

/**
 * A script can be read directly from a file or from a string.
 * This routine reads the file from dist and puts it into a string
 */
char* LuaDebugger::readFileIntoString(const char *pFileName)
{
    char line[1600];
    std::string buffer;
    FILE *fp;

    fp=fopen(pFileName, "r+");

    if(fp==NULL) {
        printf("file %s not found!\n", pFileName);
        return NULL;
    }
    else {
        while(!feof(fp)) {
            bzero(line, sizeof(line));
            fgets(line,1600,fp);
            buffer = buffer + line;
        }
    }

    fclose(fp);
    return const_cast<char*>(buffer.c_str());
}

/**
 * The script is in a string. Read the lines by finding each newline
 * and number them and put them into the maps list by setLineSourceList
 */
void LuaDebugger::readSourcefromStringBuffer(const char* source, const char* name) {
	int MAX = 170;
	char *text = new char[MAX];
	char *line = new char[MAX];
	int lineNumber = 1;
	int len = strlen(source);
	char *luaScript = new char[len+1]; // +1 for the null terminating 
	sprintf(luaScript, "%s", source);
	
	int j = 0, k = 0;
	char ch;
	while (j < len) {
		ch = luaScript[j++];
		
		if (ch == '\n' || j == len) {
			// null terminate the string
			text[k] = '\0';
			// add the script line to the list
			sprintf(line, "%d  %s", lineNumber++, text);
			LuaDebugger::setLineSourceList(name, line);
			// reset some stuff to start over
			bzero(text, sizeof(text));
			k = 0;
		} else {
			text[k++] = ch;
		}
	}
    delete [] text;
    delete [] line;
    delete [] luaScript;
}

/**
 * Read a script from disk and add the lines to the maps list.
 */
void LuaDebugger::readFile(const char *pFileName) {
	ifstream ifs(pFileName);
	int MAX = 170;
	char *text = new char[MAX];
	char *line = new char[MAX];
	int i = 1;
	
	ifstream indata; // indata is like cin

	indata.open(pFileName); // opens the file
	if(!indata) { // file couldn't be opened
	   cerr << "Error: file could not be opened" << endl;
	}

	while (indata.getline(line, MAX)) { // keep reading until end-of-file
		sprintf(text, "%d  %s", i++, line);
		LuaDebugger::setLineSourceList(pFileName, text);
		//fileLines.push_back((string)text);  
    }
	indata.close();
    delete [] line;
    delete [] text;
}

/**
 * This finds the list in the map by pFileName and then gets the line
 * at index, where index is the line number.
 */
string LuaDebugger::getFileLine(const char *pFileName, int index) {
	try {
	  // index - 1 because array starts at 0 but source lines start at 1
	  vector <string> list = allScriptsLines[pFileName];
	  
	  if (list.size() > 0) {
	      return list[index-1];
	  } else {
		  return "lines not available";
	  }
	} catch ( ... ) {
	  cout << "getFileLine: In catch handler.\n";
	  return "ERROR finding file line: " + index;
	}
}

/**
 * We are stopped at a breakpoint or stepping.
 * List the local variables at this point in execution.
 */
void LuaDebugger::listLocalVariables (lua_State *L, int level)
{
	int nLevel = level;
	lua_Debug ar;
	bool localsFound = false;
	
	printf("Finding locals:\n");
	
	if ( lua_getstack (L, nLevel, &ar) )
	{
		int i = 1;
        const char *name, *type;
        while ((name = lua_getlocal(L, &ar, i++)) != NULL) {
        	localsFound = true;
			int ntype = lua_type(L, -1);
			type = lua_typename(L, ntype);
			char value[64];
			
			switch(ntype)
			{
			case LUA_TNUMBER:
                                lua_number2str(value, lua_tonumber(L, -1));
				break;
			case LUA_TSTRING:
				sprintf(value, "%.63s", lua_tostring(L, -1));
				break;
			default:
				value[0] = '\0';
				break;
			}

			printf("%s %s=%s\n",type, name, value);

			lua_pop(L, 1);  /* remove variable value */
        }
	}
	
	if (!localsFound) {
		printf("No locals found\n");
	}
}

/*
 * List local variables name localVarName at the current runtime stack level
 */
void LuaDebugger::listLocalVariable (lua_State *L, int level, const char* localVarName)
{
	int nLevel = level;
	lua_Debug ar;
	bool localsFound = false;
	
	printf("Find local variable %s:\n", localVarName);
	
	if ( lua_getstack (L, nLevel, &ar) )
	{
		int i = 1;
        const char *name, *type;
        while ((name = lua_getlocal(L, &ar, i++)) != NULL) {
        	if (strcmp(name, localVarName) != 0) {
        		lua_pop(L, 1); // pop value, keep key for next iteration
        		continue;
        	}
        	
        	localsFound = true;
			int ntype = lua_type(L, -1);
			type = lua_typename(L, ntype);
			char value[64];
			
			switch(ntype)
			{
			case LUA_TNUMBER:
                                lua_number2str(value, lua_tonumber(L, -1));
				break;
			case LUA_TSTRING:
				sprintf(value, "%.63s", lua_tostring(L, -1));
				break;
			default:
				value[0] = '\0';
				break;
			}

			printf("%s %s=%s\n",type, name, value);

			lua_pop(L, 1);  /* remove variable value */
        }
	}
	
	if(!localsFound) {
			printf("Did not find local %s\n", localVarName);
	}
}

/*
 * This prints most globals.
 * It does not print gloabals that are functions because they are uninteresting
 * It does not currently print tables but may later
 */ 
void LuaDebugger::listGlobalVariables(lua_State *L)
{
	printf("Finding globals:\n");
	bool foundGlobals = false;
	const char *name;
	
	lua_pushvalue(L, LUA_GLOBALSINDEX);

	lua_pushnil(L);  /* first key */
	while (lua_next(L, -2))
	{
		if (lua_isfunction(L,-1)) {
			// functions are globals but do not have much info to print
			lua_pop(L, 1); // pop value, keep key for next iteration;
			continue;
		}
		
		foundGlobals = true;
		name = lua_tostring(L, -2);
		int ntype = lua_type(L, -1);
		
		char value[64];
		
		switch(ntype)
		{
		case LUA_TNUMBER:
                        lua_number2str(value, lua_tonumber(L, -1));
			break;
		case LUA_TSTRING:
			sprintf(value, "%.63s", lua_tostring(L, -1));
			break;
		default:
			sprintf(value, "%.63s", "");
			break;
		}

		printf("%s %s=%s\n",
				lua_typename(L, lua_type(L, -1)), name, value);
		
		lua_pop(L, 1); // pop value, keep key for next iteration;
	}
	lua_pop(L, 1); // pop table of globals;
	
	if(!foundGlobals) {
		printf("No globals found\n");
	}
}

/*
 * Print a name or value at the index stack location
 * 
 * value is allocated by the callee
 */
char* LuaDebugger::printIt(lua_State *L, int index, char *value) {
	int ntype = lua_type(L, index);
	
	switch(ntype)
	{
	case LUA_TNUMBER:
        lua_number2str(value, lua_tonumber(L, index));
		break;
	case LUA_TSTRING:
		sprintf(value, "%.63s", lua_tostring(L, index));
		break;
	default:
		sprintf(value, "%.63s", lua_typename(L, lua_type(L, index)));
		break;
	}
	
	return value;
}
/*
 * This method prints 1 global that matches globalVar.
 */ 
void LuaDebugger::listGlobalVariable(lua_State *L, const char* globalVarName)
{
	printf("Find global variable %s:\n", globalVarName);
	bool foundGlobals = false;
	const char *name;
	
	lua_pushvalue(L, LUA_GLOBALSINDEX);
	
	lua_pushnil(L);  /* first key */
	while (lua_next(L, -2))
	{
		name = lua_tostring(L, -2);
				
		if (strcmp(name, globalVarName) != 0) {
			// only print info about globalVar name passed in
			lua_pop(L, 1); // pop value, keep key for next iteration;
			continue;
		}
		
		foundGlobals = true;
		
		int ntype = lua_type(L, -1);
		
		char value[64];
		
		switch(ntype)
		{
		case LUA_TNUMBER:
                        lua_number2str(value, lua_tonumber(L, -1));
			break;
		case LUA_TSTRING:
			sprintf(value, "%.63s", lua_tostring(L, -1));
			break;
		default:
			sprintf(value, "%.63s", "");
			break;
		}

		if(!lua_istable(L,-1)) { 
		   printf("%s %s=%s\n",
			lua_typename(L, lua_type(L, -1)), name, value);
		} else {
		    // Print table contents.
			printf("%s %s {\n",
				lua_typename(L, lua_type(L, -1)), name);
	
		    lua_pushnil(L);  /* first key */
			char *value = new char[80];
		    while (lua_next(L, -2) != 0) {
		        printf("  %s[", name);
		        printf("%s", LuaDebugger::printIt(L, -2, value));
		        printf("]=");
		        printf("%s, \n", LuaDebugger::printIt(L, -1, value));
		        /* removes 'value'; keeps 'key' for next iteration */
		        lua_pop(L, 1);
		    }
		    printf("}\n");
		}

		lua_pop(L, 1); // pop value, keep key for next iteration;
	}
	lua_pop(L, 1); // pop table of globals;
	
	if(!foundGlobals) {
		printf("Did not find global %s\n", globalVarName);
	}
}

/**
 * This prints information about the execution stack trace of where
 * we are currently stopped.
 * 
 * Something like the following is printed:
 *   level# name,type,language,source,currentline,linedefinedat
 *   0# show,global,Lua,./sort.lua,48,40
 *   1# testsorts,global,Lua,./sort.lua,54,51
 *   2# ,,main,./sort.lua,66,0
 */
void LuaDebugger::drawStackTrace(lua_State *L)
{
	int nLevel = 0;
	lua_Debug ar;
	char szDesc[256];
	printf("level# name,type,language,source,currentline,linedefinedat\n");
	while ( lua_getstack (L, nLevel, &ar) )
	{
		lua_getinfo(L, "lnuS", &ar);
		szDesc[0] = '\0';
		if ( ar.name )
			strcat(szDesc, ar.name);
		strcat(szDesc, ",");
		if ( ar.namewhat )
			strcat(szDesc, ar.namewhat);
		strcat(szDesc, ",");
		if ( ar.what )
			strcat(szDesc, ar.what);
		strcat(szDesc, ",");
		if ( ar.source )
			strcat(szDesc, ar.source);

		printf("%i# %s,%d,%d\n",nLevel, szDesc, ar.currentline, ar.linedefined);

		++nLevel;
	};
}

/**
 * This prints information about the execution stack trace and variables.
 * A string is returned that is in the following format:
 * 
 * each stackframe delimited by #
 * each part of stackframe delimited by |
 * a single stack frame is
 * file|lineNumber|functionname|varName|etc|
 * multiple stack frames in one string would be
 * file|lineNumber|functionname|varName|etc|file|lineNumber|functionname|varName|etc|
 * 
 * Then this is parsed by the IDE to fill in stack frame and variable data
 * 
 * The variable str is allocated by the callee and can hold long strings, e.g. 2048
 */
void LuaDebugger::ideStackTrace(lua_State *L, char* str)
{
	int nLevel = 0;
	lua_Debug ar;
	char number[4];
	
	// the stack must be displayed from caller to callee (growing up)
	// so find out how many levels there are first
	while ( lua_getstack (L, nLevel, &ar) ) { nLevel++; }
	nLevel--;
	
	while ( nLevel >= 0 && lua_getstack (L, nLevel, &ar) )
	{	
		// file|lineNumber|functionname|varName|varname|etc|
		lua_getinfo(L, "lnuS", &ar);
		// first get the file name
		strcat(str,ar.source);
		
		strcat(str, "|");

		number[0] = '\0';
		sprintf(number, "%i", ar.currentline);
		strcat(str, number);
		strcat(str, "|");
		
		// now the function name
		if ( ar.name ) {
			strcat(str, ar.name);
		} else if ( ar.namewhat ) {
			if (strlen(ar.namewhat) == 0) {
				strcat(str, "main"); // toplevel of lua has no function name
			} else {
			    strcat(str, ar.namewhat);
			}
		} else if ( ar.what ) {
			strcat(str, ar.what);
		} else if ( ar.source ) {
			strcat(str, ar.source);
		} else {
			strcat(str, "unknown?");
		}
		strcat(str, "|");


		// now the varNames
		if ( lua_getstack (L, nLevel, &ar) )
		{
			int i = 1;
	        const char *name;
	        while ((name = lua_getlocal(L, &ar, i++)) != NULL) {
	        	// don't bother adding temporaries
	        	if (strcmp(name, "(*temporary)") != 0) {
				   strcat(str, name);
				   strcat(str, "|");
	        	}
	        	lua_pop(L, 1);
	        }
		}
		
		strcat(str, "#");
		
		nLevel--;
	}
}

/*
 * Pass in a stack level and a local variable name to get the value
 * 
 * The callee has allocated the return value string "val"
 * 
 * This is called from the IDE
 */
void LuaDebugger::getLocalVarValue(lua_State *L, int nLevel, char* var, char *val)
{
	lua_Debug ar;
	
	strcat(val, "notFound");

	if ( lua_getstack (L, nLevel, &ar) )
	{
		int i = 1;
        const char *name, *type;
        while ((name = lua_getlocal(L, &ar, i++)) != NULL) {
        	if (strcmp(var, name) == 0) {
				int ntype = lua_type(L, -1);
				type = lua_typename(L, ntype);
				char value[64];
				
				switch(ntype)
				{
				  case LUA_TNUMBER:
	                lua_number2str(value, lua_tonumber(L, -1));
					val[0] = '\0';
				    strcat(val, value);
					break;
				  case LUA_TSTRING:
					sprintf(value, "%.63s", lua_tostring(L, -1));
					val[0] = '\0';
				    strcat(val, value);
					break;
		          case LUA_TBOOLEAN: 
		            sprintf(value, "%s", lua_toboolean(L, i) ? "true" : "false"); 
					val[0] = '\0';
				    strcat(val, value);
		            break; 
				  default:
					if (strcmp(type, "table") == 0) {
						// make a "|" delimted list of the table values
					    // Print table contents.
						//printf("%s %s {\n",
						//	lua_typename(L, lua_type(L, -1)), name);
				
					    lua_pushnil(L);  /* first key */
					    char buf[80];
					    val[0] = '\0';
						char *printitValue = new char[80];
					    while (lua_next(L, -2) != 0) {
					    	// the index and value is printed because it
					    	// could be an associative array, x["a"]="abc"
					        sprintf(buf, "%s[", name);
					        strcat(val, buf);
					        sprintf(buf, "%s", LuaDebugger::printIt(L, -2, printitValue));
					        strcat(val, buf);
					        sprintf(buf, "]=");
					        strcat(val, buf);
					        sprintf(buf, "%s", LuaDebugger::printIt(L, -1, printitValue));
					        strcat(val, buf);
					        strcat(val, "|");
					        
					        /* removes 'value'; keeps 'key' for next iteration */
					        lua_pop(L, 1);
					    }
					    //printf("}\n");
					} else {
						sprintf(value,"type %s not yet displayable", type);
						val[0] = '\0';
					    strcat(val, value);
					}
					break;
			    } // end switch

        		lua_pop(L, 1);
        		break; // out of while
        	}

			lua_pop(L, 1);  /* remove variable value */
        }
	}
}

/* This method finds globals and returns them in a "|" delimited list
 * The "val" buffer is allocated by the callee
 * 
 * In order to handle table in the global section, the sections of
 * information look like:
 * var=val|startglobaltable=startglobaltable|var=val|etc|endglobaltable=endglobaltable
 * To use indenting to make it more clear:
 * var=val|
 *         startglobaltable=startglobaltable|
 *         var=val|
 *         etc|
 *         endglobaltable=endglobaltable|
 * var=val|
 * etc|
 * 
 * This is the easiest way to serialize structure data.
 * Note that table of tables is not handled.
 */
void LuaDebugger::getGlobals(lua_State *L, char *val)
{
	const char *name, *type;
	
	val[0] = '\0';
	
	lua_pushvalue(L, LUA_GLOBALSINDEX);

	lua_pushnil(L);  /* first key */
	while (lua_next(L, -2))
	{
		int i = 1;
        
		if (lua_isfunction(L,-1)) {
			// functions are globals but do not have much info to print
			lua_pop(L, 1); // pop value, keep key for next iteration;
			continue;
		}
		
		name = lua_tostring(L, -2);

		int ntype = lua_type(L, -1);
		type = lua_typename(L, ntype);
		char value[64];
		
		switch(ntype)
		{
		  case LUA_TNUMBER:
            lua_number2str(value, lua_tonumber(L, -1));
			strcat(val,name);
			strcat(val,"=");
		    strcat(val, value);
		    strcat(val, "|");
			break;
		  case LUA_TSTRING:
			sprintf(value, "%.63s", lua_tostring(L, -1));
			strcat(val,name);
			strcat(val,"=");
		    strcat(val, value);
		    strcat(val, "|");
			break;
          case LUA_TBOOLEAN: 
            sprintf(value, "%s", lua_toboolean(L, i) ? "true" : "false"); 
			strcat(val,name);
			strcat(val,"=");
		    strcat(val, value);
		    strcat(val, "|");
            break; 
		  default:
			if (strcmp(type, "table") == 0) {
				// careful "package[config]" has funny chars in it
				
				// make a "|" delimted list of the table values
			    // Print table contents.
				//printf("%s %s {\n",
				//	lua_typename(L, lua_type(L, -1)), name);
		
			    lua_pushnil(L);  /* first key */
			    char buf[80];
			    char tmpbuf[200];
			    strcat(val, "startglobaltable=startglobaltable|");
				char value[80];
			    while (lua_next(L, -2) != 0) {
				    tmpbuf[0] = '\0';
				    
			    	// the index and value is printed because it
			    	// could be an associative array, x["a"]="abc"
			        sprintf(buf, "%s[", name);
			        strcat(tmpbuf, buf);
			        sprintf(buf, "%s", LuaDebugger::printIt(L, -2, value));
			        strcat(tmpbuf, buf);
			        sprintf(buf, "]=");
			        strcat(tmpbuf, buf);
			        sprintf(buf, "%s", LuaDebugger::printIt(L, -1, value));
			        strcat(tmpbuf, buf);
			        strcat(tmpbuf, "|");
			        
			    	// don't waste time for chars with newlines, these blow things
			    	// up in the IDE
				    if (strchr(tmpbuf, '\n') != NULL ||
				    	strchr(tmpbuf, 13) != NULL) {
				    	lua_pop(L, 1);
				    	continue;
				    } 
				    
				    strcat(val, tmpbuf);
			        /* removes 'value'; keeps 'key' for next iteration */
			        lua_pop(L, 1);
			    }
			    strcat(val, "endglobaltable=endglobaltable|");
			    //printf("}\n");
			} else {
				sprintf(value,"type %s = not yet displayable", type);
				val[0] = '\0';
			    strcat(val, value);
			}
			break;
	    } // end switch

		lua_pop(L, 1);  /* remove variable value */
	}
	
	lua_pop(L, 1); // pop table of globals;
}


void LuaDebugger::stackDump (lua_State *L) { 
      char value[64];
      int i=lua_gettop(L);  
      while(  i   ) { 
        int t = lua_type(L, i); 
        switch (t) { 
          case LUA_TSTRING: 
            printf("%d:`%s'\n", i, lua_tostring(L, i)); 
          break; 
          case LUA_TBOOLEAN: 
            printf("%d: %s\n",i,lua_toboolean(L, i) ? "true" : "false"); 
          break; 
          case LUA_TNUMBER: 
            lua_number2str(value, lua_tonumber(L, -1));
            printf("%d: %s\n",  i, value); 
         break; 
         default: printf("%d: %s\n", i, lua_typename(L, t)); break; 
        } 
       i--; 
      }  
} 

/*
 * This collects the Lua stack information in a "|" separated list
 * and returns it in the var "results" that is allocated by the callee
 */
void LuaDebugger::stackDumpForIde (lua_State *L, char *results) { 
      char value[64];
      int top = lua_gettop(L); 
      int i;
      
      for (i=1; i<=top; i++) {
        int t = lua_type(L, i); 
        switch (t) { 
          case LUA_TSTRING: 
        	  strcat(results, lua_tostring(L, i)); 
              break; 
          case LUA_TBOOLEAN: 
        	  strcat(results, lua_toboolean(L, i) ? "true" : "false");
              break; 
          case LUA_TNUMBER: 
            lua_number2str(value, lua_tonumber(L, -1));
            strcat(results, value); 
            break; 
         default: strcat(results, lua_typename(L, t)); break; 
        }
        strcat(results, "|");
      }  
} 

void LuaDebugger::whereAmI(lua_State* L, lua_Debug* ar) {
	switch (*ar->namewhat) 
	{
		case 'g':  /* global */ 
		case 'l':  /* local */
		case 'f':  /* field */
		case 'm':  /* method */
			printf(" in function `%s'", ar->name);
		break;
		default: 
		{
			if (*ar->what == 'm')  /* main? */
				printf(" in main chunk");
			else if (*ar->what == 'C')  /* C function? */
				printf("%s", ar->source);
			else
				printf(" in function <%s:%d>", ar->source, ar->linedefined);
		}

	}
	printf("\n");
}

void LuaDebugger::printBreakpoints(const char * scriptName, vector<int> bp) {
	if (bp.size() == 0) {
		printf("no breakpoints set\n");
	} else {
		printf("printBreakpoints: \n");
		for(int i=0;i < (int)bp.size(); i++)
		{
			int breakPoint = bp.at(i);
			printf(" %s line %i\n", scriptName, breakPoint);
		}
	}
}

int LuaDebugger::listvars (lua_State *L, int level) {
  lua_Debug ar;
  int i;
  const char *name;
  if (lua_getstack(L, level, &ar) == 0)
    return 0;  /* failure: no such level in the stack */
  i = 1;
  while ((name = lua_getlocal(L, &ar, i++)) != NULL) {
    printf("local %d %s\n", i-1, name);
    lua_pop(L, 1);  /* remove variable value */
  }
  lua_getinfo(L, "f", &ar);  /* retrieves function */
  i = 1;
  while ((name = lua_getupvalue(L, -1, i++)) != NULL) {
    printf("upvalue %d %s\n", i-1, name);
    lua_pop(L, 1);  /* remove upvalue value */
  }
  return 1;
}

void LuaDebugger::commandParser(lua_State *L, lua_Debug *ar) {
	
	const int lineLen = 160;
	char line[lineLen];
    char *pCmd;
    char *pLine;
    char *p;
    int lineNumber;
    bool keepGoing = true;
    bool commandError = false;

    if (debugger == ide) {
    	// printf("event commands\n");
    	ostringstream ostr;
        //ostr << "started" << endl;
        //eventSocket << ostr.str();
        ostr << "resumed client" << endl;
        eventSocket << ostr.str();
        ostr << "suspended step" << endl;
        eventSocket << ostr.str();
        // printf("sent event commands\n");
    }
    
    // some commands like "step" and "continue" resume execution while all
    // the others do not. The ones that do not will just keep looping here
    while (keepGoing) {
    	keepGoing = true;
    	if (debugger == ldb) {
	        printf("ldb> ");
	        cin.getline(line, sizeof(line));
    	} else {
    		std::string data;
    		try {
                // printf("trying to get socket data\n");
    		    newSocket >> data;

    		    if (data.empty()) {
    		    	printf("No command given\n");
    		    	continue;
    		    }

    		    // did we get all the command input
    		    size_t found = data.find("\n");
    		    int max = 5;
    		    int count = 0;
    		    while (found == string::npos) {
    		    	std::string tmpstr = data;
    		    	newSocket >> data;
    		    	data = tmpstr + data;
    		    	found = data.find("\n");
    		    	if (count++ >= max) {
    		    		keepGoing = false;
    		    		printf("could not get full command string after %d times", count);
    		    		break; // something wrong, don't loop forever
    		    	}
    		    }
    		    // printf("got socket data %s\n", data.c_str());
    		} catch ( SocketException& ) { 
    			printf("received socket exception\n");
    			return;
    		}
    		
            strncpy(line, const_cast<char*>(data.c_str()), lineLen-1);
            if ((int)data.length() > lineLen) {
                // never happens with the small strings of commands, safety
                line[lineLen-1] = '\0';
            }
    		
    	}
	    
	    if (strlen(line) == 0) {
	    	continue; // they just hit a carriage return with no command
	    }
	    
	    //  Now test for, and remove that newline character
	    if ((p = strchr(line, '\n')) != NULL) {
	          *p = '\0';
	    }
	    if ((p = strchr(line, 13)) != NULL) {
	        *p = '\0';
	    }
	    
	    pCmd = strtok(line, " ");
	    commandError = false;
	    
	    // Only a few letters of a command are needed, e.g. l for list
	    if (strstr(pCmd, getGlobalsCmd) == pCmd) {
	    	char val[8096];
	    	
	    	getGlobals(L, val);
	    
	    	if (debugger == ldb) {
	    		cout << val;
	    	} else {
	    		ostringstream ostr;
	    	    ostr << val << endl;
	    		newSocket << ostr.str();
	    	}

	    } else if (strstr(pCmd, dataForIdeCmd) == pCmd) {
	    	char val[1024];
	    	val[0] = '\0';
	    	stackDumpForIde(L, val);
	    	if (debugger == ldb) {
	    		cout << val;
	    	} else {
	    		ostringstream ostr;
	    	    ostr << val << endl;
	    		newSocket << ostr.str();
	    	}
	    } else if (strstr(pCmd, varForIdeCmd) == pCmd) {
	    	char *pFrameNumber = strtok(NULL, " ");
	        // atoi returns 0 for null or non-digit input
	        int frameNumber = atoi(pFrameNumber);
	        
	        char *var = strtok(NULL, " ");
	        
	        // large because it could be table values returned
	    	char val[8096];
	    	val[0] = '\0';
	        
	    	LuaDebugger::getLocalVarValue(L, frameNumber, var, val);
	    	if (debugger == ldb) {
	    		cout << val;
	    	} else {
	    		ostringstream ostr;
	    	    ostr << val << endl;
	    		newSocket << ostr.str();
	    	}
	    } else if (strstr(pCmd, stackForIdeCmd) == pCmd) {
    		char str[2048];
    		str[0] = '\0';
    		LuaDebugger::ideStackTrace(L, str);
	    	if (debugger == ldb) {
	    		cout << str;
	    	} else {
	    		//printf("idestack: %s", str);
	    		//newSocket << str;
	    		ostringstream ostr;
	    	    ostr << str << endl;
	    		newSocket << ostr.str();
	    	}
	    } else if (strstr(pCmd, suspendCmd) == pCmd) {
	    	// the "suspend" button of the IDE has been clicked
	    	if (debugger == ldb) {
	    		cout << "ok";
	    	} else {
	    		newSocket << "ok\n";
	    		
	    		ostringstream ostr;
	            ostr << "suspended client" << endl;
	            eventSocket << ostr.str();
	    	}
	    } else if (strstr(pCmd, listCmd) == pCmd) {
	    	int firstLine, lastLine;
	    	vector <string> list = allScriptsLines[ar->source];
	    	int numScriptLines = list.size();
	    	
	    	// syntax:
	    	//   list firstline [lastline]
	    	pLine = strtok(NULL, " ");	 
	    	if (pLine == NULL) {
	    		// they forgot it, just make it 1
	    		firstLine = 1;
	    	} else {
                firstLine = atoi(pLine);
	    	}
	    	
	    	if (firstLine == 0) {
	    		// atoi returns 0 when not a digit
	    		commandError = true;
	    	} else {
	            // get the optional lastLine parameter
	            pLine = strtok(NULL, " ");
	            if (pLine == NULL) {
	            	// print either 5 more lines or until the
	            	// end of the script, whichever is less
	            	if (firstLine + 5 < numScriptLines) {
	            		lastLine = firstLine + 5;
	            	} else {
	            		lastLine = numScriptLines;
	            	}
	            } else {
	            	// the lastLine parameter was supplied
	            	lastLine = atoi(pLine);
	            	if (lastLine > numScriptLines) {
	            		lastLine = numScriptLines;
	            	} else if (lastLine == 0) {
	            		// atoi returns 0 when not a digit
	            		commandError = true;
	            	}
	            }
	    	}
	    	
	    	if (commandError) {
	    		printf(invalidCommand);
	    	} else {
	            lua_getinfo(L, "Sl", ar);
	            	  
	            for (int i = firstLine; i <= lastLine; i++) {
		    	    string l = LuaDebugger::getFileLine(ar->source, i);
		    	    if (debugger == ldb) {
				        cout << l << "\n";
		    	    } else {
		    	    	// don't write to the command socket
		    	    	// newSocket << l << "\n";
		    	    	// we probably don't need this in an IDE because it
		    	    	// already has the lines
		    	    }
	            }
	    	}

	    } else if (strstr(pCmd, setbreakCmd) == pCmd) {
	    	// set a breakpoint
	    	// syntax is:
	    	//   break line
	    	pLine = strtok(NULL, " ");
	    	// atoi returns 0 for null or non-digit input
	    	lineNumber = atoi(pLine); 
	    	if (pLine == NULL || lineNumber == 0) {
	    		printf("Syntax error, invalid or no line number\n");
	    	} else {
		    	LuaDebugger::setBreakPointList(ar->source, lineNumber);
		    	ostringstream ostr;
	    		ostr << "setting breakpoint at " << ar->source << " line " << lineNumber << endl;
	    		// ostr << LuaDebugger::getFileLine(ar->source, lineNumber) << endl;
		    	// printf("setting breakpoint at %s line %i \n", ar->source, lineNumber);
		    	// string l = ostr.str() + 
		    	//  LuaDebugger::getFileLine(ar->source, lineNumber);
		    	if (debugger == ldb) {
				    cout << ostr.str();
		    	} else {
		    		newSocket << ostr.str();
		    	}
	    	}
	    } else if (strstr(pCmd, clrbreakCmd) == pCmd) {
	    	// remove the breakpoint
	    	// syntax is:
	    	//    clear line
	    	pLine = strtok(NULL, " ");
	    	// atoi returns 0 for null or non-digit input
	    	lineNumber = atoi(pLine);
	    	if (pLine == NULL || lineNumber == 0) {
	    		printf("Syntax error, invalid or no line number\n");
	    	} else {
	    		vector<int> list = getBreakPointList(ar->source);
	    		
	    		// remove from the breakPoint vector
	    		std::vector<int>::iterator itVectorData;
	    		for(itVectorData = list.begin(); itVectorData != list.end(); itVectorData++)
	    		{
	    			int breakPoint = *(itVectorData);
	    			if (breakPoint == lineNumber) {
	    				list.erase(itVectorData);
	    				allScriptsBreakpoints[ar->source] = list;
		    			
				    	if (debugger == ldb) {
				    		printf("removing breakpoint at %s line %i \n", ar->source, lineNumber);
				    	} else {
				    		newSocket << "ok clear\n";
				    	}
		    			break;
	    			}
	    		}
	    	}
	    } else if (strstr(pCmd, displayCmd) == pCmd) {
	    	// syntax is:
	    	//  display [varname]
	    	const char* varName = strtok(NULL, " ");
	    	if (varName == NULL) {
	    		// display all variables
	    		LuaDebugger::listLocalVariables(L, 0);
	    		LuaDebugger::listGlobalVariables(L);
	    	} else {
	    		// Display the variable named varname whether
	    		// it is local, global or upvalue.
	    		// Users may not know which it is or there could be
	    		// an issue where one variable accidentally hides another
	    		LuaDebugger::listLocalVariable (L, 0, varName);
		    	LuaDebugger::listGlobalVariable(L, varName);
	    	}
	    } else if (strstr(pCmd, assignCmd) == pCmd) {
	        printf("assign command not implemented\n");
	    } else if (strstr(pCmd, stepCmd) == pCmd) {
	        // syntax is:
	        //   step
	        //
	    	// just set the state to step so that the next time the callback
	    	// function is called by Lua, the parser will be called again,
	    	// effectively imitating a "step"
	
	    	debuggerStates = STEPPING;
	    	
	    	keepGoing = false;
	    	
	    	if (debugger == ldb) {
	    		cout << "ok";
	    	} else {
	    		newSocket << "ok\n";
	    		
	    		ostringstream ostr;
	    		ostr << "resumed step" << endl;
	    	    eventSocket << ostr.str();
	    	    ostr << "suspended step" << endl;
	    	    eventSocket << ostr.str();
	    	}
	    } else if (strstr(pCmd, continueCmd) == pCmd ||
	    		   strstr(pCmd, resumeCmd) == pCmd) {
	        // syntax is:
	        //   continue
	    	//
	    	// just set the state to continue so that the each time the callback
	    	// function is called by Lua (line by line), we will only stop if
	    	// a breakpoint (func/line) matches the current func/line
	    	debuggerStates = CONTINUING;
	    	
	    	keepGoing = false;
	    	
	    	if (debugger == ldb) {
	    		cout << "ok";
	    	} else {
	    		// printf("sending ok from continue\n");
	    		newSocket << "ok resume\n";
	    		
	    		ostringstream ostr;
	    		ostr << "resumed client" << endl;
	    		eventSocket << ostr.str();
	    	}
	    } else if (strstr(pCmd, printCmd) == pCmd) {
	        // syntax is:
	        //   print [stack|trace|breakpoints file|stackforide]
	    	const char* which = strtok(NULL, " ");
	    	if (strcmp(which, "stack") == 0) {
	    	    stackDump(L);
	    	} else if (strcmp(which, "trace") ==  0) {
	    		drawStackTrace(L);
	    	} else if (strcmp(which, "breakpoints") == 0) {
	    		vector<int> list = getBreakPointList(ar->source);
	    		LuaDebugger::printBreakpoints(ar->source, list);
	    	} else {
	    		printf("Syntax error for print command. Don't know how to print %s\n", which);
	    	}
	    } else if (strstr(pCmd, quitCmd) == pCmd) {
	        // syntax is:
	        //   quit
	    	
	    	// disable the mask of the callback so it is not called again
	    	// this means that it will not be possible to debug anymore
                //
                // do this until quit sematics are finalized
                debuggerStates = CONTINUING;

                keepGoing = false;

	    } else if (strstr(pCmd, helpCmd) == pCmd) {
	        // syntax is:
	    	//   help
	    	printf("commands can be shortened to 1 or 2 letters, e.g. l for list\n");
	    	printf("%s", allCmds);
	    } else {
	    	if (debugger == ldb) {
	    	    printf("ERROR, command not found: %s\n",pCmd);
	    	} else {
	    		ostringstream ostr;
	    		ostr << "ERROR, command not found: " <<  pCmd << endl;
	    		newSocket << ostr.str();
	    	}
	    }
	    
    } // end while
    
}

void HookRoutine(lua_State *L, lua_Debug *ar)
{ 
  // Only listen to "Hook Lines" events
  if(ar->event == LUA_HOOKLINE)
  {
	  // 'S' fills in source, linedefined and what
	  // 'l' fills in line number
          // printf("source: %s\n", ar->source);
          // printf("short_src: %s\n", ar->short_src);

	  lua_getinfo(L, "Sl", ar);
  	  if (debuggerStates == CONTINUING) {
		  std::vector<int>::iterator itVectorData;
		  vector <int> list = allScriptsBreakpoints[ar->source];
		  for(itVectorData = list.begin(); itVectorData != list.end(); itVectorData++)
		  {
			int breakPoint = *(itVectorData);
			
			// check for linenumber matches
			if (breakPoint == ar->currentline) { 
				// We simulate CONTINUING by only calling the parser, which
				// prompts, when we have hit a breakpoint
				// debug for now
				// whereAmI(L, ar);
			    // print the line that was just executed
				
				string l = LuaDebugger::getFileLine(ar->source, ar->currentline);
				if (debugger == ldb) {
					printf("\nstopping at breakpoint in %s line %i \n", ar->source, breakPoint);
				    cout << l << "\n";
				} else {
					// don't write to the command soocket
					// newSocket << l << "\n";
					// notify the debugger IDE of hitting breakpoint
					ostringstream ostr;
					ostr << "resumed client" << endl;
					eventSocket << ostr.str();
				    ostr << "suspended breakpoint " << breakPoint << endl;
				    eventSocket << ostr.str();
				}
				
				//printf("  %i %s\n", ar->currentline, ar->source);
		        LuaDebugger::commandParser(L, ar);
		        break;
			}
		  } // end for
  	  } else if (debuggerStates == STEPPING) {
		  // some debug stuff for now
		  // whereAmI(L, ar);
		  
		  // const char* localName = lua_getlocal (L, ar, 1);
		  // printf("local %s\n", localName);
		  
		  // printf("  %i %s\n", ar->currentline, ar->source);
		  
		  // print the line that was just executed
		  string l = LuaDebugger::getFileLine(ar->source, ar->currentline);
		  if (debugger == ldb) {
		      cout << l << "\n";
		  } else {
			  // don't write to the command socket
			  // newSocket << l << "\n";
			  // just notify the debugger ide that step has finished
			  //ostringstream ostr;
			  //ostr << "resumed step" << endl;
			  //eventSocket << ostr.str();
			  //ostr << "suspended step" << endl;
			  //eventSocket << ostr.str();
		  }
		  				
		  // The command parser, which prompts, is called on every step
	      LuaDebugger::commandParser(L, ar);
	  } else {
		  printf("Error: unknown state %d", debuggerStates);
	  }
  }
}

int LuaDebugger::OutputTop(lua_State* L)
{
	printf(luaL_checkstring(L, -1));
	printf("\n");
	return 0;
}

#define LEVELS1	12	/* size of the first part of the stack */
#define LEVELS2	10	/* size of the second part of the stack */
int LuaDebugger::errormessage(lua_State *L)
{
	int level = 1;  /* skip level 0 (it's this function) */
	int firstpart = 1;  /* still before eventual `...' */
	lua_Debug ar;
	if (!lua_isstring(L, 1))
		return lua_gettop(L);
	lua_settop(L, 1);
	lua_pushliteral(L, "\n");
	lua_pushliteral(L, "stack traceback:\n");
	while (lua_getstack(L, level++, &ar)) 
	{
		char buff[10];
		if (level > LEVELS1 && firstpart) 
		{
			/* no more than `LEVELS2' more levels? */
			if (!lua_getstack(L, level+LEVELS2, &ar))
				level--;  /* keep going */
			else 
			{
				lua_pushliteral(L, "       ...\n");  /* too many levels */
				while (lua_getstack(L, level+LEVELS2, &ar))  /* find last levels */
					level++;
			}
			firstpart = 0;
			continue;
		}
    
		sprintf(buff, "%4d-  ", level-1);
		lua_pushstring(L, buff);
		lua_getinfo(L, "Snl", &ar);
		lua_pushfstring(L, "%s:", ar.short_src);
		if (ar.currentline > 0)
			lua_pushfstring(L, "%d:", ar.currentline);
		switch (*ar.namewhat) 
		{
			case 'g':  /* global */ 
			case 'l':  /* local */
			case 'f':  /* field */
			case 'm':  /* method */
				lua_pushfstring(L, " in function `%s'", ar.name);
			break;
			default: 
			{
				if (*ar.what == 'm')  /* main? */
					lua_pushfstring(L, " in main chunk");
				else if (*ar.what == 'C')  /* C function? */
					lua_pushfstring(L, "%s", ar.short_src);
				else
					lua_pushfstring(L, " in function <%s:%d>", ar.short_src, ar.linedefined);
			}
    
		}

		lua_pushliteral(L, "\n");
		lua_concat(L, lua_gettop(L));
	}
  
	lua_concat(L, lua_gettop(L));
  
	LuaDebugger::OutputTop(L);

	return 0;
}

// Change to main for testing
// The args are:
// ldb -n scriptName -b true/false -t ide/ldb -c commandPort -e eventPort
//        argv[2]       argv[4]       argv[6]    argv[8]        argv[10]
//
// The argv[2] tru/false is whether to buffer the script or not
int main(int argc, char * argv[])
{	
	const char* luaScriptName = NULL;
	const char* loadFromBuffer = NULL;
	const char* debuggerType = NULL;
	bool bufferScript = true;
	int commandPort = 0;
	int eventPort = 0;
	
	if (argc != 11) {
		printf("argc=%i\n", argc);
		printf("All params must be given:\n  -n scriptName -b true/false -t ide/ldb -c commandPort -e eventPort\n");
		printf("%s", argv[0]);
		int i;
		for (i = 1; i < argc; i++) {
			printf(" %s\n", argv[i]);
		}
		printf("\n");
		return (1); // all params must be given
	}
	
	// start at 1 skipping the command name
	int i;
	for (i = 1; i < argc; i++) {

		/* Check for a switch (leading "-"). */

		if (argv[i][0] == '-') {

		    /* Use the next character to decide what to do. */

		    switch (argv[i][1]) {

		    // -n for name
			case 'n':	luaScriptName = argv[++i];
			    break;

		    // -b for buffer or not, true or false
			case 'b':	loadFromBuffer = argv[++i];
			    if (strcmp(loadFromBuffer, "true") == 0) {
			      	bufferScript = true;
			    } else {
			       	bufferScript = false;
			    }
				break;

		    // -t for type of debugger, ide or ldb
			case 't':	debuggerType = argv[++i];
			    if (strcmp(debuggerType, "ide") == 0) {
			      	debugger = ide;
			    } else {
			       	debugger = ldb;
			    }			
			    break;

			// -c for commandPort number
			case 'c':	commandPort = atoi(argv[++i]);
				break;

			// -e for eventPort number
			case 'e':	eventPort = atoi(argv[++i]);
				break;

		    }
		}
	}

    printf("Script name: %s", luaScriptName);
    printf(" Buffer: %i", bufferScript);
    printf(" Debugger type: %i", debugger);
    printf(" Command port: %i", commandPort);
    printf(" Event port: %i\n", eventPort);
    
	printf("Starting Lua Debugger %i\n", debugger);
	
	if (debugger == ide) {
		try {
		      // Create the socket
		      ServerSocket server ( commandPort );
	
			  server.accept (newSocket);
			  printf("accepted socket %i\n", commandPort);
		} catch ( SocketException& e ) {
		      cout << "Exception was caught:" << e.description() << "\nExiting.\n";
		}
		 
		try {
		      // Create the socket
		      ServerSocket eventServer ( eventPort );
	
		      eventServer.accept (eventSocket);
		      printf("accepted socket %i\n", eventPort);
		} catch ( SocketException& e ) {
		      cout << "Exception was caught starting event socket server:" << e.description() << "\nExiting.\n";
		}
		
		try {
			ostringstream ostr;
			ostr << "started" << endl;
			eventSocket << ostr.str();
		} catch ( SocketException& e ) {
		      cout << "Exception was caught sending started event:" << e.description() << "\nExiting.\n";
		}
	}
	
	LuaDebugger d;
	
	if (bufferScript == false) {
		printf("running Lua script %s\n", luaScriptName);
				
		// Open the LUA state
	    // Open lua and load standard libs
		// Open the LUA state
		lua_State *L = lua_open();
	
	    /* Open the standard Lua libraries... such as table, string, math, and the base functions. You don't really need this... but you most definitely will want to call this */
	    luaL_openlibs(L);
	
	    // register the hook that is called on each lua script line
	    lua_sethook(L, HookRoutine, LUA_MASKLINE, 0);
	    
	    LuaDebugger::readFile(luaScriptName);
	    
	    /* Load & run hello.lua, without error checking. This will 
	     * generate a warning in gcc because you're not checking 
	     * the return value (luaL_dofile is a macro) */
	    luaL_dofile(L, luaScriptName); 
	    /* Note: all lua functions begin 
	    with L as the first arg - think of this as self in Lua scripts. 
	    this is so you can have several lua instances at the same time */
	
	    /* Clean up. This must be called at end */
	    lua_close(L);
		
		return (0);
	} else {
		  int error;
		  
		  char* scriptInBuffer = LuaDebugger::readFileIntoString(luaScriptName);
		  
		  LuaDebugger::readSourcefromStringBuffer(scriptInBuffer, luaScriptName);
		  
		  lua_State * L = lua_open();   /* opens Lua */
		  //luaopen_base(L);              /* opens the basic library */
		  /* Open the standard Lua libraries... such as table, string, math, and the base functions. You don't really need this... but you most definitely will want to call this */
		  luaL_openlibs(L);

		  /* stack the debug.traceback function */
		  lua_getglobal(L, "debug");
		  lua_getfield(L, -1, "traceback");
		  lua_remove(L, -2);
		  
          // register the hook that is called on each lua script line
		  lua_sethook(L, HookRoutine, LUA_MASKLINE, 0);

		  error = 
		     luaL_loadbuffer (L, scriptInBuffer, strlen (scriptInBuffer), luaScriptName) ||
		     lua_pcall (L, 0, 0, -3);
		          
		  if (error)
		    {
		    fprintf (stderr, "%s\n", lua_tostring (L, -1));
		    
		    try {
		        ostringstream ostr;
		        ostr << "syntaxerror | " << luaScriptName << " | " << lua_tostring (L, -1);
		        eventSocket << ostr.str();
			} catch ( SocketException& e ) {
			      cout << "Exception was caught sending syntaxerror event:" << e.description() << "\nExiting.\n";
			}

		    LuaDebugger::errormessage(L);
		    lua_pop (L, 1);
		    //lua_error(L);
		    }  

		  lua_close (L);
		  return error;
	}
}


