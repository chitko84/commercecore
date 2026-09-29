@ECHO OFF
@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

SET BASE_DIR=%~dp0
SET BASE_DIR=%BASE_DIR:~0,-1%
SET WRAPPER_JAR="%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar"
SET WRAPPER_PROPERTIES="%BASE_DIR%\.mvn\wrapper\maven-wrapper.properties"
SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

IF NOT "%JAVA_HOME%"=="" (
  SET JAVA_EXE="%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET JAVA_EXE=java.exe
)

IF NOT EXIST %WRAPPER_JAR% (
  FOR /F "tokens=1,* delims==" %%A IN ('findstr /r "^wrapperUrl=" %WRAPPER_PROPERTIES%') DO powershell -Command "Invoke-WebRequest -Uri '%%B' -OutFile %WRAPPER_JAR%"
)

%JAVA_EXE% -classpath %WRAPPER_JAR% "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" %WRAPPER_LAUNCHER% %*
