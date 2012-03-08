/*
 * Copyright 2012 ClamShell-Cli.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.clamshellcli.jmx;

/**
 *
 * @author vvivien
 */
public interface TestJmxMBeanMBean {
    public static final String NAME="test.jmx:type=bean";
    public void setStringValue(String val);
    public String getStringValue();
    public Integer getNumericValue();
    public void setNumericValue(Integer val);
    public void setBooleanValue(boolean val);
    public boolean getBooleanValue();
    
    public void exec();
    public void exec(String val);
    public void execWithParam(String val);
    public void execWithParam(Integer val);
    public void execWithParams(String val1, Integer val2);
    public String retrieveValue();
}
