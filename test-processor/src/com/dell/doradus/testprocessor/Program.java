/*
 * Copyright (C) 2014 Dell, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dell.doradus.testprocessor;

import com.dell.doradus.testprocessor.common.*;
import com.dell.doradus.testprocessor.diff.*;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class Program
{
    static public void main(String args[])
    {
        try {
            Data.predefine();

            if (Data.configFilePath == null || Data.configFilePath.trim().isEmpty()) {
                System.out.println("Config file is not define: Using predefined data\"");
            }
            else {
                System.out.println("Loading config \"" + Data.configFilePath + "\"");
                Config.load(Data.configFilePath);
                Config.modifyData();
            }

            Log.toFile(Data.logFilePath);
            if (Log.isOpened()) {
                System.out.println("Log: \"" + Data.configFilePath + "\"");
            }

            Log.println("*** Program: Test Processor Data:");
            Log.println(Data.toString("    "));

            boolean ignoreWhiteSpace = true;
            boolean ignoreCase = true;
            Differ differ = new Differ(ignoreWhiteSpace, ignoreCase);

            if (Data.testSuiteInfo == null) {
                String msg = "Test suite is not defined";
                throw new Exception(msg);
            }

            Log.println("*** Program: Running Tests");

            System.out.println("Running Tests");
            for (TestDirInfo testDirInfo : Data.testSuiteInfo.getTestDirInfoList()) {
                System.out.println("Directory: " + testDirInfo.path());

                for (TestInfo testInfo : testDirInfo.testInfoList()) {
                    if (testInfo.isExcluded()) continue;
                    System.out.print("   " + testInfo.name() + ": ");
                    try { TestProcessor.execute(testInfo); }
                    catch(Exception ex) { System.out.println(); }
                    System.out.println(testInfo.resultToString());
                }
            }

            if (Data.reportFilePath != null)
            {
                Log.println("*** Program: Generating tests report:  " + Data.reportFilePath);
                System.out.println("Report: \"" + Data.reportFilePath + "\"");

                if (FileUtils.fileExists(Data.reportFilePath))
                    FileUtils.deleteFile(Data.reportFilePath);

                OutputStream reportOutputStream = new FileOutputStream(Data.reportFilePath);
                Writer reportWriter = new OutputStreamWriter(reportOutputStream);

                String report = Reporter.generateHtmlReport(Data.testSuiteInfo);

                reportWriter.write(report);
                reportWriter.flush();
                reportWriter.close();
            }
        }
        catch (Exception ex) {
            Log.println("!!! Exception: " + Utils.unwind(ex));
            System.out.println("!!! Exception: " + Utils.unwind(ex));
        }
        finally {
            Log.close();
        }
    }
}
