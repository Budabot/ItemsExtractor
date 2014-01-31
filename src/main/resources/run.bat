@echo off
set /p aoPath= Enter the full path to AO (ex: C:\Funcom\Anarchy Online): 
java -cp ./lib/*; com.jkbff.ao.itemsextractor.Program -d "%aoPath%"
pause