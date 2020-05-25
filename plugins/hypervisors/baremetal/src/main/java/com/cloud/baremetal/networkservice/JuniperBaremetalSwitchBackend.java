// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
// Automatically generated by addcopyright.py at 01/29/2013
// Apache License, Version 2.0 (the "License"); you may not use this
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

package com.cloud.baremetal.networkservice;

import com.cloud.baremetal.manager.BaremetalManagerImpl;
import com.cloud.baremetal.manager.VlanType;
import com.cloud.utils.exception.CloudRuntimeException;
import net.juniper.netconf.Device;
import net.juniper.netconf.NetconfException;
import net.juniper.netconf.XML;
import net.juniper.netconf.XMLBuilder;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JuniperBaremetalSwitchBackend implements BaremetalSwitchBackend {

    private static final Logger s_logger = Logger.getLogger(JuniperBaremetalSwitchBackend.class);
    public static final String TYPE = "Juniper";
    public static final int NETCONF_PORT = 22;

    @Override
    public String getSwitchBackendType() {
        return TYPE;
    }

    @Override
    public synchronized void prepareVlan(BaremetalVlanStruct struct) {
        JuniperDevice juniper = null;
        CloudRuntimeException cloudRuntimeException = null;
        try {
            juniper = new JuniperDevice(struct.getSwitchIp(), NETCONF_PORT, struct.getSwitchUsername(), struct.getSwitchPassword());
            juniper.addVlanToInterface(struct.getPort(), struct.getVlan(), struct.getVlanType());
        } catch (ParserConfigurationException e) {
            String mesg = "Invalid configuration to initiate netconf session to the backend switch";
            s_logger.error(mesg, e);
            cloudRuntimeException = new CloudRuntimeException(mesg, e);
        } catch (SAXException | IOException | XPathExpressionException e) {
            String mesg = "Unable to add VLAN to Port";
            s_logger.error(mesg, e);
            cloudRuntimeException =  new CloudRuntimeException(mesg, e);
        }
        closeConnection(juniper, cloudRuntimeException);
    }

    @Override
    public synchronized void removePortFromVlan(BaremetalVlanStruct struct) {
        JuniperDevice juniper = null;
        CloudRuntimeException cloudRuntimeException = null;
        try {
            juniper = new JuniperDevice(struct.getSwitchIp(), NETCONF_PORT, struct.getSwitchUsername(), struct.getSwitchPassword());
            if (struct.isRemoveAll()) {
                juniper.clearAllVlansFromInterface(struct.getPort(), struct.getVlanType());
            } else {
                juniper.removeVlanFromInterface(struct.getPort(), struct.getVlan(), struct.getVlanType());
            }
        } catch (ParserConfigurationException e) {
            String mesg = "Invalid configuration to initiate netconf session to the backend switch";
            s_logger.error(mesg, e);
            cloudRuntimeException = new CloudRuntimeException(mesg, e);
        } catch (SAXException | IOException e) {
            String mesg = String.format("Unable to remove VLAN %d from Port: %s, type : %s", struct.getVlan(), struct.getPort(), struct.getVlanType());
            s_logger.error(mesg, e);
            cloudRuntimeException = new CloudRuntimeException(mesg, e);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        closeConnection(juniper, cloudRuntimeException);
    }

    private void closeConnection(JuniperDevice juniper, CloudRuntimeException cloudRuntimeException) {
        if(juniper != null) {
            juniper.close();
        }
        if(cloudRuntimeException != null) {
            throw cloudRuntimeException;
        }
    }

    public class JuniperDevice {
        Device device;

        public JuniperDevice(String host, int port, String user, String password) throws ParserConfigurationException, NetconfException {
            device = new Device(host, user, password, null, port);
            device.connect();
        }

        protected void close() {
            device.close();
        }

        public void addVlanToInterface(String interfaceName, int vlanId, VlanType vlanType) throws IOException, SAXException, XPathExpressionException, ParserConfigurationException {
            String configTemplate = "set interfaces %s unit 0 family ethernet-switching vlan members %d \n" +
                    "set interfaces %s unit 0 family ethernet-switching interface-mode trunk\n";

            if(vlanId == BaremetalManagerImpl.pxeVlan.value()) {
                configTemplate += String.format("set protocols lldp interface %s enable\n", interfaceName);
            } else {
                configTemplate += String.format("delete protocols lldp interface %s\n", interfaceName);
            }

            if (vlanType.equals(VlanType.UNTAGGED)) {
                configTemplate += String.format("set interfaces %s native-vlan-id %d", interfaceName, vlanId);
            }

            String config = String.format(configTemplate, interfaceName, vlanId, interfaceName);

            this.device.loadSetConfiguration(config);
            this.device.commit();
        }

        public void removeVlanFromInterface(String interfaceName, int vlanId, VlanType vlanType) throws SAXException, ParserConfigurationException, XPathExpressionException, IOException {
            String config = String.format("delete interfaces %s unit 0 family ethernet-switching vlan members %d\n", interfaceName, vlanId);
            if (vlanType.equals(VlanType.UNTAGGED)) {
                config += String.format("delete interfaces %s native-vlan-id \n", interfaceName);
            }

            boolean lastVlan = getInterfaceVlans(interfaceName).size() == 1;

            if (lastVlan) {
                config += String.format("delete interfaces %s unit 0 family ethernet-switching interface-mode", interfaceName);
            }

            this.device.loadSetConfiguration(config);
            this.device.commit();
        }

        void clearAllVlansFromInterface(String interfaceName, VlanType vlanType) throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
            String config = "";

            for (int vl : this.getInterfaceVlans(interfaceName)) {
                if (vl > 1)  {
                    config += String.format("delete interfaces %s unit 0 family ethernet-switching vlan members %d\n", interfaceName, vl);
                }
            }

            if(vlanType.equals(VlanType.UNTAGGED)) {
                config = String.format("delete interfaces %s native-vlan-id \n", interfaceName);
            }

            config += String.format("delete interfaces %s unit 0 family ethernet-switching interface-mode", interfaceName);

            this.device.loadSetConfiguration(config);
            this.device.commit();
        }

        private List<Integer> getInterfaceVlans(String interfaceName) throws ParserConfigurationException, XPathExpressionException {
            List<Integer> interfaceVlans = new ArrayList<>();

            XMLBuilder rpcBuilder = new XMLBuilder();
            XML vlanQuery = rpcBuilder.createNewRPC("get-ethernet-switching-interface-information").append("interface-name", interfaceName + ".0");
            XML out = getConfig(vlanQuery.toString());

            assert out != null;

            Document doc = out.getOwnerDocument();
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile("//l2iff-interface-vlan-id");

            NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            for (int i =0; i<nl.getLength(); i++) {
                Node node = nl.item(i);
                String vlanText = node.getTextContent();
                interfaceVlans.add(Integer.valueOf(vlanText));
            }

            return interfaceVlans;
        }

        private String getVlanName(int vlanId) throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
            XMLBuilder rpcBuilder = new XMLBuilder();
            XML vlanQuery = rpcBuilder.createNewRPC("get-vlan-information").append("vlan-name", Integer.toString(vlanId));
            XML out = getConfig(vlanQuery.toString());

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile("//l2ng-l2rtb-vlan-name");

            NodeList xPathResult = (NodeList) expr.evaluate(out.getOwnerDocument(), XPathConstants.NODESET);

            if(xPathResult.getLength() != 1) {
                return null;
            }

            return xPathResult.item(0).getTextContent();
        }

        private XML getConfig(String req) {
            try {
                XML config = device.executeRPC(req);
                return config;
            } catch (SAXException | IOException e) {
                throw new CloudRuntimeException("Unable to get config ", e);
            } finally {
            }
        }
    }
}
