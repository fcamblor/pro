@echo off
if "%JAVA_HOME%" == "" set "JAVA_HOME=C:\Program Files\Java\jdk-10"
set "java=%JAVA_HOME%\bin\java"
set "javac=%JAVA_HOME%\bin\javac"

if exist bootstrap rmdir /s /q bootstrap 
mkdir bootstrap

dir /s /b /a:-d src\main\java > bootstrap\files.txt

"%javac%" --module-source-path src\main\java ^
          -d bootstrap\modules\ ^
          --module-path deps ^
          @bootstrap\files.txt

"%java%" --module-path bootstrap/modules;deps ^
         --upgrade-module-path bootstrap/modules ^
         --module com.github.forax.pro.bootstrap/com.github.forax.pro.bootstrap.Bootstrap
