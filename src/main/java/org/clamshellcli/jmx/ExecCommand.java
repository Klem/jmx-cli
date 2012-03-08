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

import org.clamshellcli.api.Command;
import org.clamshellcli.api.Context;
import org.clamshellcli.api.IOConsole;
import org.clamshellcli.core.ShellException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ReflectionException;

/**
 * The Exec command lets user invoke methods on specified mbean.
 * The format for the exec command is:
 * <pre>
 * exec bean:<MBeanObjectName,MBeanLabel> 
 *      get:<attribName>|[[<attribNameList>]]
 *      set:<attribName>|[[<attribNameList>]]
 *      op:<operationName>|[[<opNameList>]]
 *      params:[<paramValueList>]
 * </pre>
 * @author vladimir.vivien
 */
public class ExecCommand implements Command{
   public static final String CMD_NAME         = "exec";
    public static final String NAMESPACE       = "jmx";
    public static final String KEY_ARGS_BEAN   = "bean";
    public static final String KEY_ARGS_GET    = "get";
    public static final String KEY_ARGS_SET    = "set";
    public static final String KEY_ARGS_OP     = "op";
    public static final String KEY_ARGS_PARAMS = "params";
    
    private Command.Descriptor descriptor = null;
 
    public Descriptor getDescriptor() {
        return (descriptor != null ) ? descriptor : (
            descriptor = new Command.Descriptor() {

                public String getNamespace() {
                    return NAMESPACE;
                }

                public String getName() {
                    return CMD_NAME;
                }

                public String getDescription() {
                    return "Execute MBean operations and getter/setter attributes.";
                }

                public String getUsage() {
                    return "exec bean:<MBeanNamePattern> [get:<AttributeName> "
                            + "set:<AttributeName> op:<OperationName> "
                            + "params:<ParamList>]";
                }

                Map<String,String> args;
                public Map<String, String> getArguments() {
                    if(args != null) return args;
                    args = new LinkedHashMap<String,String>();
                    args.put(KEY_ARGS_BEAN      + ":<NamePattern>", "The object name pattern for the bean(s)");
                    args.put(KEY_ARGS_BEAN      + ":<MBeanLabel>", "A bean label that refers to an MBean.");
                    args.put(KEY_ARGS_GET       + ":<AttributeName>", "An attribute to retrieve");  
                    args.put(KEY_ARGS_SET       + ":<AttributeName>", "Name of attribute to set (use 'params:' for value)");
                    args.put(KEY_ARGS_OP        + ":<OperationName>", "Name of an operation to invoke");
                    args.put(KEY_ARGS_PARAMS    + ":<ParamValue>", "A parameter value used for operation or setter");
                    args.put(KEY_ARGS_PARAMS    + ":[ParamList]", "A list of two or more parameter values used for operation or setter");
                    return args;
                }
            }
        );
    }

    public Object execute(Context ctx) {
        IOConsole c = ctx.getIoConsole();
        List<Object> result = null;
        Map<String, Object> argsMap = (Map<String, Object>) ctx.getValue(Context.KEY_COMMAND_LINE_ARGS);
        Map<String, ObjectInstance> mbeanMap = (Map<String, ObjectInstance>) ctx.getValue(Management.KEY_MBEANS_MAP);

        // validate connection
        Management.verifyServerConnection(ctx);
        MBeanServerConnection server = (MBeanServerConnection) ctx.getValue(Management.KEY_JMX_MBEANSERVER);

        Object mbeanParam = (argsMap  != null) ? argsMap.get(KEY_ARGS_BEAN) : null;
        Object getParam  = (argsMap  != null) ? argsMap.get(KEY_ARGS_GET) : null;
        Object setParam  = (argsMap  != null) ? argsMap.get(KEY_ARGS_SET) : null;
        Object opParam   = (argsMap  != null) ? argsMap.get(KEY_ARGS_OP) : null;
        Object params    = (argsMap  != null) ? argsMap.get(KEY_ARGS_PARAMS) : null;
        
        // valdate name params
        if (opParam != null && setParam != null) {
            throw new ShellException("You cannot specify both 'op:' "
                    + "and 'set:' at the same time (see help).");
        }
   
        result = new ArrayList<Object>();
        
        ObjectInstance[] objs = Management.findObjectInstances(ctx, (String)mbeanParam);
        if(objs == null || objs.length == 0){
            throw new ShellException(String.format("No MBeans found %s.",mbeanParam));
        }

        for (ObjectInstance obj : objs) {
            // get attribute
            if(getParam != null){
                result.add(getObjectAttribute(server, obj, (String) getParam));
                c.writeOutput(String.format("%n%s ==> %s", getParam, result));
            }

            // set attribute
            if(setParam != null){
                if(params instanceof List){
                    params = ((List)params).toArray();
                }
                this.setObjectAttribute(server, obj, (String)setParam, params);
                c.writeOutput(String.format("%n%s ==> %s", setParam, params));
            }

            if(opParam != null){
                Object[] paramVals = null;
                if(params instanceof List){
                    paramVals = ((List)params).toArray();
                }else{
                    paramVals = (params != null) ? new Object[]{params} : null;
                }
                // find all matching operation from object
                List<MBeanOperationInfo> ops = null;
                try {
                    ops = findOpsBySignature(server, obj, (String)opParam, paramVals);
                } catch (Exception ex) {
                    throw new ShellException(ex);
                }

                // invoke all matching op
                if(ops != null && ops.size() > 0){
                    for(MBeanOperationInfo op : ops){
                        try{
                            Object val = invokeObjectOperation(server, obj, op, paramVals);
                            result.add(val);
                            c.writeOutput(String.format(
                                "%n%s(%s) : %s",
                                opParam, 
                                (paramVals != null) ? Arrays.asList(paramVals) : "", 
                                (val != null) ? val : "void"));
                        }catch(ShellException ex){
                            c.writeOutput(String.format(
                                "Operation %s.%s(%s) failed: %s", 
                                obj.getClassName(), 
                                opParam, 
                                Arrays.asList(getOpSignature(op)),
                                ex.getMessage()));
                        }
                    }
                }else{
                    throw new ShellException(String.format(
                        "Method %s.%s() not found.", 
                        obj.getClassName(), 
                        opParam));
                }
            }
        }
         
        
        c.writeOutput(String.format("%n%n"));
        
        return result;
    }

    public void plug(Context plug) {
    }
    
    private Object getObjectAttribute(MBeanServerConnection server, ObjectInstance obj, String attrib) throws ShellException {
        Object result = null;
        try {
            result = server.getAttribute(obj.getObjectName(), attrib);
        } catch (MBeanException ex) {
            throw new ShellException(ex);
        } catch (AttributeNotFoundException ex) {
            throw new ShellException(ex);
        } catch (InstanceNotFoundException ex) {
            throw new ShellException(ex);
        } catch (ReflectionException ex) {
            throw new ShellException(ex);
        } catch (IOException ex) {
            throw new ShellException(ex);
        }catch(Exception ex){
            throw new ShellException(ex);
        }
        
        return result;
    }
    
    private void setObjectAttribute(MBeanServerConnection server, ObjectInstance obj, String attrib, Object value){
        try {
            server.setAttribute(obj.getObjectName(), new Attribute(attrib,value));
        } catch (InstanceNotFoundException ex) {
            throw new ShellException(ex);
        } catch (AttributeNotFoundException ex) {
            throw new ShellException(ex);
        } catch (InvalidAttributeValueException ex) {
            throw new ShellException(ex);
        } catch (MBeanException ex) {
            throw new ShellException(ex);
        } catch (ReflectionException ex) {
            throw new ShellException(ex);
        } catch (IOException ex) {
            throw new ShellException(ex);
        }catch(Exception ex){
            throw new ShellException(ex);
        }
    }
    
    /**
     * Invokes one or more operation that matches the specified op name and
     * parameter signature.
     * @param server
     * @param obj
     * @param opName
     * @param params
     * @return
     * @throws ShellException 
     */
    private Object invokeObjectOperation(MBeanServerConnection server, ObjectInstance obj, MBeanOperationInfo op, Object[] params) throws ShellException{
        String[] signature = getOpSignature(op);
        Object result = null;
        try {
            result = server.invoke(obj.getObjectName(), op.getName(), params, signature);
        } catch (InstanceNotFoundException ex) {
            throw new ShellException(ex);
        } catch (MBeanException ex) {
            throw new ShellException(ex);
        } catch (ReflectionException ex) {
            throw new ShellException(ex);
        } catch (IOException ex) {
            throw new ShellException(ex);
        }catch(Exception ex){
            throw new ShellException(ex);
        }
        
        return result;
    }
    
    /**
     * Return all operations which have the same name and signature size.
     * @param server
     * @param obj
     * @param opName
     * @param params
     * @return
     * @throws Exception 
     */
    private List<MBeanOperationInfo> findOpsBySignature(MBeanServerConnection server, ObjectInstance obj, String opName, Object[] params) throws Exception{
        MBeanOperationInfo[] ops = server.getMBeanInfo(obj.getObjectName()).getOperations();
        if(ops == null)
            return null;
        
        List<MBeanOperationInfo> result = new ArrayList<MBeanOperationInfo>(ops.length);
        for(MBeanOperationInfo op : ops){
            int paramLen = (params != null) ? params.length : 0;            
            if (op.getName().equals(opName) && op.getSignature().length == paramLen) {
                result.add(op);
            }
        }
        
        return result;
    }
    
    private String[] getOpSignature(MBeanOperationInfo op){
        MBeanParameterInfo[] sig = op.getSignature();
        String[] result = new String[sig.length];
        for(int i = 0; i < sig.length; i++){
            result[i] = sig[i].getType();
        }
        return result;
    }
}
