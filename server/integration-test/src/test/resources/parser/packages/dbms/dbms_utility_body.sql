CREATE or REPLACE package body dbms_utility AS
  ARRAY_TYPE_UNCL                 CONSTANT BINARY_INTEGER := 1;
  ARRAY_TYPE_LNAME                CONSTANT BINARY_INTEGER := 2;
  MAX_BYTES_PER_CHAR              CONSTANT NUMBER := 4;
  MAX_LNAME_LENGTH                CONSTANT BINARY_INTEGER := 4000;

  function format_call_stack return varchar2;
    pragma interface (C, format_call_stack);
  function format_error_stack return varchar2;
    pragma interface (C, format_error_stack);
  function format_error_backtrace return varchar2;
    pragma interface (C, format_error_backtrace);
  procedure name_tokenize( NAME IN VARCHAR2, A OUT VARCHAR2, B OUT VARCHAR2, C OUT VARCHAR2,
                           DBLINK OUT VARCHAR2,
                           NEXTPOS OUT BINARY_INTEGER);
    pragma interface (C, name_tokenize);


  PROCEDURE ICD_NAME_RES(NAME IN VARCHAR2, CONTEXT IN NUMBER,
    SCHEMA1 OUT VARCHAR2, PART1 OUT VARCHAR2, PART2 OUT VARCHAR2,
    DBLINK OUT VARCHAR2, PART1_TYPE OUT NUMBER, OBJECT_NUMBER OUT NUMBER);
    pragma interface (C, name_resolve);
    
  PROCEDURE NAME_RESOLVE(NAME IN VARCHAR2, CONTEXT IN NUMBER,
    SCHEMA1 OUT VARCHAR2, PART1 OUT VARCHAR2, PART2 OUT VARCHAR2,
    DBLINK OUT VARCHAR2, PART1_TYPE OUT NUMBER, OBJECT_NUMBER OUT NUMBER) IS
  BEGIN
    IF (CONTEXT != 0 AND CONTEXT != 1 AND CONTEXT != 2 AND CONTEXT != 3 AND
        CONTEXT != 4 AND CONTEXT != 5 AND CONTEXT != 6 AND CONTEXT != 7 AND
        CONTEXT != 8 AND CONTEXT != 9 AND CONTEXT != 10) THEN
    RAISE_APPLICATION_ERROR(-20005,
           'ORU-10034: context argument must be integral, 0 to 10');
    END IF;
    ICD_NAME_RES(NAME, CONTEXT, SCHEMA1, PART1, PART2, DBLINK, PART1_TYPE,
      OBJECT_NUMBER);
  END;

  FUNCTION ICD_GET_ENDIANNESS RETURN NUMBER;
  PRAGMA INTERFACE (C, GET_ENDIAN);

  FUNCTION GET_ENDIANNESS RETURN NUMBER IS
  BEGIN
    RETURN ICD_GET_ENDIANNESS;
  END;

  PROCEDURE ICD_GET_PARAM_VALUE(PARNAM  IN      VARCHAR2,
                      PARTYP  IN OUT  BINARY_INTEGER,
                      INTVAL  IN OUT  BINARY_INTEGER,
                      STRVAL  IN OUT  VARCHAR2,
                      LISTNO  IN      BINARY_INTEGER);
  PRAGMA INTERFACE (C, GET_PARAM_VALUE);

  FUNCTION GET_PARAMETER_VALUE(PARNAM IN     VARCHAR2,
                               INTVAL IN OUT BINARY_INTEGER,
                               STRVAL IN OUT VARCHAR2,
                               LISTNO IN     BINARY_INTEGER DEFAULT 1)
    RETURN BINARY_INTEGER IS
  PARTYP BINARY_INTEGER;
  BEGIN
    IF (LISTNO <= 0) OR (LISTNO IS NULL) THEN
      RAISE_APPLICATION_ERROR(-20000,
        'get_parameter_value: listno value must be a positive value');
    END IF;
   IF PARNAM IS NULL THEN
      RAISE_APPLICATION_ERROR(-20000,
        'get_parameter_value: input parameter must not be null');
    END IF;
    ICD_GET_PARAM_VALUE(PARNAM, PARTYP, INTVAL, STRVAL, LISTNO);
    IF INTVAL IS NULL AND STRVAL IS NULL THEN
      RAISE_APPLICATION_ERROR(-20000,
        'get_parameter_value: invalid or unsupported parameter "'||
         SUBSTR(PARNAM,1,1000)||'"');
    END IF;
    RETURN PARTYP;
    EXCEPTION
      WHEN OTHERS THEN
        RAISE;
  END GET_PARAMETER_VALUE;

  FUNCTION ICD_GETSQLHASH(NAME IN VARCHAR2, HASH OUT RAW,
                          PRE10IHASH OUT NUMBER) RETURN NUMBER;
    PRAGMA INTERFACE (C, GETSQLHASH);
  FUNCTION GET_SQL_HASH(NAME IN VARCHAR2, HASH OUT RAW, 
                        PRE10IHASH OUT NUMBER)
    RETURN NUMBER IS
  BEGIN
    RETURN (ICD_GETSQLHASH(NAME, HASH, PRE10IHASH));
  END GET_SQL_HASH;

  FUNCTION RAWS(BIT_OFFSET IN NUMBER) RETURN RAW IS
  BEGIN
    IF BIT_OFFSET = 1 THEN RETURN HEXTORAW('01');
    ELSIF BIT_OFFSET = 2 THEN RETURN HEXTORAW('02');
    ELSIF BIT_OFFSET = 3 THEN RETURN HEXTORAW('04');
    ELSIF BIT_OFFSET = 4 THEN RETURN HEXTORAW('08');
    ELSIF BIT_OFFSET = 5 THEN RETURN HEXTORAW('10');
    ELSIF BIT_OFFSET = 6 THEN RETURN HEXTORAW('20');
    ELSIF BIT_OFFSET = 7 THEN RETURN HEXTORAW('40');
    ELSE RETURN HEXTORAW('80');
    END IF;
  END RAWS;
  FUNCTION BIT_XX(FLAG       IN RAW,
               BYTE       IN NUMBER,
               BIT_OFFSET IN NUMBER) RETURN BOOLEAN IS
  BIT_FLAG RAW(1) := RAWS(BIT_OFFSET);
  BEGIN
    RETURN UTL_RAW.BIT_AND(UTL_RAW.SUBSTR(FLAG, BYTE, 1), BIT_FLAG) = BIT_FLAG;
  END BIT_XX;

  FUNCTION IS_BIT_SET(R IN RAW, N IN NUMBER) 
  RETURN NUMBER IS
  BEGIN
    IF (BIT_XX(R, 4 - (TRUNC((N-1)/8,0)), MOD((N-1), 8) + 1)) THEN 
      RETURN (1); 
    ELSE 
      RETURN (0); 
    END IF;
  END IS_BIT_SET;

  PROCEDURE ICD_CANAM(NAME              IN  VARCHAR2,
                     CANON_LEN         IN  BINARY_INTEGER,
                     CANON_NAME        OUT VARCHAR2);
    PRAGMA INTERFACE (C, CANONICALIZE);
  PROCEDURE CANONICALIZE(NAME           IN   VARCHAR2,
                         CANON_NAME     OUT  VARCHAR2,
                         CANON_LEN      IN   BINARY_INTEGER) IS
    NAME_LENGTH   NUMBER;
    DUMMY         NUMBER := 1;
  BEGIN
    IF NAME IS NULL THEN
      CANON_NAME := NULL;
      RETURN;
    END IF;
    ICD_CANAM(NAME, CANON_LEN, CANON_NAME);
  END CANONICALIZE;

  PROCEDURE TABLE_TO_COMMA( TAB    IN  UNCL_ARRAY, 
                            TABLEN OUT BINARY_INTEGER,
                            LIST   OUT VARCHAR2) IS
    TEMP  VARCHAR2(32512) := '';
    I     BINARY_INTEGER  :=  1;
  BEGIN
    IF TAB(I) IS NOT NULL THEN
      TEMP := TAB(I);
      I    := I + 1;
      WHILE TAB(I) IS NOT NULL LOOP
        TEMP := TEMP || ',' || TAB(I);
        I := I + 1;
      END LOOP;
    END IF;
    TABLEN := I-1;
    LIST   := TEMP;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      TABLEN := I-1;
      LIST   := TEMP; 
  END TABLE_TO_COMMA;

  PROCEDURE TABLE_TO_COMMA( TAB    IN  LNAME_ARRAY, 
                            TABLEN OUT BINARY_INTEGER,
                            LIST   OUT VARCHAR2) IS
    TEMP  VARCHAR2(32512) := '';
    I     BINARY_INTEGER  :=  1;
  BEGIN
    IF TAB(I) IS NOT NULL THEN
      TEMP := TAB(I);
      I    := I + 1;
      WHILE TAB(I) IS NOT NULL LOOP
        TEMP := TEMP || ',' || TAB(I);
        I := I + 1;
      END LOOP;
    END IF;
    TABLEN := I-1;
    LIST   := TEMP;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      TABLEN := I-1;
      LIST   := TEMP; 
  END TABLE_TO_COMMA;


  PROCEDURE COMMA_TO_TABLE( LIST        IN  VARCHAR2,
                          ARRAY_TYPE  IN  BINARY_INTEGER,
                          TABLEN      OUT BINARY_INTEGER,
                          TAB_U       OUT UNCL_ARRAY,
                          TAB_A       OUT LNAME_ARRAY ) IS
  NEXTPOS     BINARY_INTEGER;
  OLDPOS      BINARY_INTEGER;
  DONE        BOOLEAN;
  I           BINARY_INTEGER;
  LEN         BINARY_INTEGER;
  DUMMY       VARCHAR2(2000);
  ERR_SQLCODE PLS_INTEGER;
  BEGIN
    NEXTPOS  := 1;
    DONE     := FALSE;
    I        := 1;
    LEN      := NVL(LENGTHB(LIST),0);
    WHILE NOT DONE LOOP
      OLDPOS := NEXTPOS;
      IF ARRAY_TYPE = ARRAY_TYPE_UNCL THEN
        DBMS_UTILITY.NAME_TOKENIZE( SUBSTRB(LIST,OLDPOS),
                             DUMMY, DUMMY, DUMMY, DUMMY, NEXTPOS );
        TAB_U(I) := SUBSTRB( LIST, OLDPOS, NEXTPOS );
      ELSE
        NULL;
        -- DBMS_UTILITY.LNAME_PARSE( SUBSTRB(LIST,OLDPOS), MAX_LNAME_LENGTH, NEXTPOS );
        -- TAB_A(I) := SUBSTRB( LIST, OLDPOS, NEXTPOS );
      END IF;
      NEXTPOS := OLDPOS + NEXTPOS;
      IF NEXTPOS > LEN THEN
        DONE := TRUE;
      ELSIF SUBSTRB(LIST,NEXTPOS,1) = ',' THEN
        NEXTPOS := NEXTPOS + 1;
      ELSE 
        RAISE_APPLICATION_ERROR( -20001, 
          'comma-separated list invalid near ' || SUBSTRB(LIST,NEXTPOS-2,5));
      END IF;
      I := I + 1;
    END LOOP;
    IF ARRAY_TYPE = ARRAY_TYPE_UNCL THEN
      TAB_U(I) := NULL;
    ELSE
      NULL;
      -- TAB_A(I) := NULL;
    END IF;
    TABLEN := I-1;
  EXCEPTION
    WHEN OTHERS THEN
      ERR_SQLCODE := SQLCODE;
      IF ERR_SQLCODE <= -900 AND ERR_SQLCODE >= -999 THEN
        RAISE_APPLICATION_ERROR( -20001, 
          'comma-separated list invalid near ' || SUBSTRB(LIST, OLDPOS-2, 5));
      ELSE
        RAISE;
      END IF;
  END COMMA_TO_TABLE;

  -- PROCEDURE COMMA_TO_TABLE( LIST   IN  VARCHAR2, 
  --                           TABLEN OUT BINARY_INTEGER,
  --                           TAB    OUT UNCL_ARRAY ) IS
  --   TAB_DUMMY  LNAME_ARRAY;
  -- BEGIN
  --   COMMA_TO_TABLE(LIST, ARRAY_TYPE_UNCL, TABLEN, TAB, TAB_DUMMY);
  -- END COMMA_TO_TABLE;

  -- PROCEDURE COMMA_TO_TABLE( LIST   IN  VARCHAR2, 
  --                           TABLEN OUT BINARY_INTEGER,
  --                           TAB    OUT LNAME_ARRAY ) IS
  --   TAB_DUMMY  UNCL_ARRAY;
  -- BEGIN
  --   COMMA_TO_TABLE(LIST, ARRAY_TYPE_LNAME, TABLEN, TAB_DUMMY, TAB);
  -- END COMMA_TO_TABLE;

  FUNCTION ICD_GET_DBV RETURN VARCHAR2;
  PRAGMA INTERFACE (C,GET_DB_VERSION);

  PROCEDURE DB_VERSION(VERSION       OUT VARCHAR2,
                       COMPATIBILITY OUT VARCHAR2) IS
  BEGIN
    VERSION := ICD_GET_DBV;
    COMPATIBILITY := NULL;
  END DB_VERSION;

  FUNCTION PORT_STRING RETURN VARCHAR2;
  PRAGMA INTERFACE(C, GET_PORT_STRING);

  FUNCTION GET_HASH_VALUE(NAME VARCHAR2, BASE NUMBER, HASH_SIZE NUMBER) RETURN NUMBER;
  PRAGMA INTERFACE(C, GET_HASH_VALUE);

  FUNCTION ICD_GET_TIME RETURN BINARY_INTEGER;
  PRAGMA INTERFACE (C, GET_TIME);

  FUNCTION GET_TIME RETURN NUMBER IS
  BEGIN
    RETURN ICD_GET_TIME;
  END GET_TIME;

  FUNCTION IS_CLUSTER_DATABASE RETURN BOOLEAN IS
  BEGIN
    RETURN TRUE;
  END IS_CLUSTER_DATABASE;

  FUNCTION CURRENT_INSTANCE RETURN NUMBER IS
  CUR_INST NUMBER;
  BEGIN
    SELECT INSTANCE_NUMBER INTO CUR_INST
      FROM SYS.V$INSTANCE;
  RETURN CUR_INST;
  EXCEPTION WHEN NO_DATA_FOUND THEN
     RETURN NULL;
  END CURRENT_INSTANCE;

  PROCEDURE ACTIVE_INSTANCES (INSTANCE_TABLE OUT INSTANCE_TABLE,
                            INSTANCE_COUNT OUT NUMBER) AS
  CURSOR C1 IS SELECT INSTANCE_NUMBER, INSTANCE_NAME FROM SYS.GV$INSTANCE
                ORDER BY INSTANCE_NUMBER, INSTANCE_NAME;
  INSTANCE_REC INSTANCE_RECORD;
  BEGIN 
    INSTANCE_COUNT := 0;
    FOR REC IN C1 LOOP
      INSTANCE_COUNT :=INSTANCE_COUNT + 1;
      INSTANCE_REC.INST_NUMBER := REC.INSTANCE_NUMBER;
      INSTANCE_REC.INST_NAME := REC.INSTANCE_NAME;
      INSTANCE_TABLE(INSTANCE_COUNT) := INSTANCE_REC;
    END LOOP;
  END ACTIVE_INSTANCES;

  FUNCTION OLD_CURRENT_SCHEMA RETURN VARCHAR2 IS
    ocs VARCHAR2(4000);
  BEGIN 
    SELECT sys_context('userenv', 'current_schema') into ocs from dual;
    RETURN ocs;
  END OLD_CURRENT_SCHEMA;
  
  FUNCTION OLD_CURRENT_USER RETURN VARCHAR2 IS
    ocu VARCHAR2(4000);
  BEGIN 
    SELECT sys_context('userenv', 'current_user') into ocu from dual;
    RETURN ocu;
  END OLD_CURRENT_USER;
END
//