/*
 * Copyright 2011 ClamShell-Cli.
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

import org.clamshellcli.api.Context;
import org.clamshellcli.jmx.Management.VmInfo;
import org.clamshellcli.test.MockContext;
import java.util.HashMap;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import org.junit.Test;

/**
 *
 * @author vladimir
 */
public class ConnectCommandTest {
    Context ctx = MockContext.createInstance();
    ConnectCommand cmd;
    Map<String,String> argsMap;
    
    public ConnectCommandTest() {
        cmd = new ConnectCommand();
        argsMap = new HashMap<String,String>();
    }
    
    @Test
    public void testExecuteWithDefaultHostArgs () throws Exception{
        JmxAgent agent = TestUtils.startNewJmxAgent(1099);
        
        ctx.putValue(Context.KEY_COMMAND_LINE_ARGS, argsMap);
        cmd.execute(ctx);
        JMXServiceURL url = (JMXServiceURL) ctx.getValue(Management.KEY_JMX_URL);
        JMXConnector c = (JMXConnector) ctx.getValue(Management.KEY_JMX_CONNECTOR);
        assert url != null;
        assert url.toString().equals(Management.getJmxUrlFrom("localhost").toString());
        assert c == null;  
        
        agent.stop();
    }
    
    @Test
    public void testExecuteWithHostArgs() throws Exception {
        JmxAgent agent = TestUtils.startNewJmxAgent(1999);
        
        argsMap.put(Management.KEY_ARGS_HOST, "localhost:1999");
        
        ctx.putValue(Context.KEY_COMMAND_LINE_ARGS, argsMap);
        cmd.execute(ctx);
        
        JMXServiceURL url = (JMXServiceURL) ctx.getValue(Management.KEY_JMX_URL);
        JMXConnector c = (JMXConnector) ctx.getValue(Management.KEY_JMX_CONNECTOR);
        MBeanServerConnection cn = (MBeanServerConnection) ctx.getValue(Management.KEY_JMX_MBEANSERVER);

        assert url != null;
        assert c   != null;
        assert cn  != null;
       
        
        agent.stop();
    }
    
    @Test
    public void testExecuteWithPidArgs() throws Exception{
        JmxAgent agent = TestUtils.startNewJmxAgent(1999);
        
        Map<Integer,VmInfo> vms = Management.mapVmInfo("localhost");
        Integer vmId = vms.keySet().iterator().next();
        argsMap.put(Management.KEY_ARGS_PID, vmId.toString());
        
        ctx.putValue(Context.KEY_COMMAND_LINE_ARGS, argsMap);
        cmd.execute(ctx);
        
        JMXServiceURL url = (JMXServiceURL) ctx.getValue(Management.KEY_JMX_URL);
        JMXConnector c = (JMXConnector) ctx.getValue(Management.KEY_JMX_CONNECTOR);
        MBeanServerConnection cn = (MBeanServerConnection) ctx.getValue(Management.KEY_JMX_MBEANSERVER);

        assert url != null;
        assert c   != null;
        assert cn  != null;
       
        agent.stop();
        
    }
}
