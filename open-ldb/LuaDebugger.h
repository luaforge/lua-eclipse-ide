/**********************************************************

    Copyright (c) 2007 VeriSign, Inc.  All rights reserved.

    This software is provided solely in connection with the terms of
    the license agreement. Any other use without the prior express
    written permission of VeriSign is completely prohibited.  The
    software and documentation are "Commercial Items", as that term
    is defined in 48 C.F.R. Section 2.101, consisting of "Commercial
    Computer Software" and "Commercial Computer Software
    Documentation" as such terms are defined in 48 C.F.R. Section
    252.227-7014(a)(5) and 48 C.F.R. Section 252.227-7014(a)(1), and
    used in 48 C.F.R. Section 12.212 and 48 C.F.R. Section 227.7202,
    as applicable.  Pursuant to the above and other relevant sections
    of the Code of Federal Regulations, as applicable, VeriSign's
    publications, commercial computer software, and commercial
    computer software documentation are distributed and licensed to
    United States Government end users with only those rights as
    granted to all other end users, according to the terms and
    conditions contained in the license agreement(s) that accompany
    the products and software documentation.

***********************************************************/
#if !defined LUA_DEBUGGER_H
#define      LUA_DEBUGGER_H

#ifdef WORKING_ON_PC
#ifdef __cplusplus
extern "C" {
#endif
#include "lua.h"
#include "lualib.h"
#include "lauxlib.h"
#ifdef __cplusplus
}
#endif


#include <stdlib.h>
#include <string.h>
#include <iostream>
using std::cerr;
using std::cout;
using std::endl;
#include <fstream>
using std::ifstream;
#include <vector>
#include <map>
#include <string>
#include <sstream>
#include "ServerSocket.h"
#include "SocketException.h"

using namespace std;

extern void HookRoutine(lua_State *L, lua_Debug *ar);

#else

#ifdef __cplusplus
extern "C" {
#endif
#include "../lua/src/lua.h"
#include "../lua/src/lualib.h"
#include "../lua/src/lauxlib.h"
#ifdef __cplusplus
}
#endif


#include <stdlib.h>
#include <string.h>
#include <iostream>
using std::cerr;
using std::cout;
using std::endl;
#include <fstream>
using std::ifstream;
#include <vector>
#include <map>
#include <string>
using namespace std;

extern void HookRoutine(lua_State *L, lua_Debug *ar);

#endif

/*
 * ldb - a cmdline debugger with input/output via stdin/stdout
 * ide - an eclipse plugin with input/output over sockets
 */
enum debuggerType {ldb, ide};

class LuaDebugger {
public:

    LuaDebugger();
    ~LuaDebugger() { }
    
static void readSourcefromStringBuffer(const char* source, const char* name);
static char* readFileIntoString(const char *pFileName);
static string getFileLine(const char *pFileName, int index);
static void commandParser(lua_State *L, lua_Debug *ar);
static void setBreakPointList(const char* scriptName, int lineNumber);
static void listLocalVariables (lua_State *L, int level);
static void listLocalVariable (lua_State *L, int level, const char* localVarName);
static void listGlobalVariables(lua_State *L);
static void listGlobalVariable(lua_State *L, const char* globalVarName);
static void drawStackTrace(lua_State *L);
static void stackDump (lua_State *L);
static void printBreakpoints(const char * scriptName, vector<int> bp);
static char* printIt(lua_State *L, int index, char *value);
static void readFile(const char *pFileName);
static int errormessage(lua_State *L);
static void setLineSourceList(const char* scriptName, char* text);
static int OutputTop(lua_State* L);
static void ideStackTrace(lua_State *L, char* str);
static void getLocalVarValue(lua_State *L, int nLevel, char* var, char *val);
static void stackDumpForIde (lua_State *L, char *results);
static void getGlobals(lua_State *L, char *val);

private:

void whereAmI(lua_State* L, lua_Debug* ar); 
int listvars (lua_State *L, int level); 


};

#if !defined WORKING_ON_PC

#endif
#endif  // #if !defined LUA_DEBUGGER_H
