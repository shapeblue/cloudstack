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
(function(cloudStack) {
    cloudStack.sections.system = {
        title: 'label.retrievediagnostics.diagnostics',
        listView: {
            id: 'retrieveDiagnostics',
            fields: {
                name: {
                    label: 'label.name'
                },
                type: {
                    label: 'label.type'
                }
            },

            var selectedZoneObj = args.context.physicalResources[0];
            $.ajax({
                    url: createURL("listSystemVms&zoneid=" + selectedZoneObj.id ),
                    dataType: "json"
                    async: true
                    success: function(json) {
                        var items = json.listsystemvmsresponse.systemvm;
                        args.response.success({
                            data: items
                        });
                    }
                });
            },
            actions: {
                add: {
                    label: 'label.retrievediagnostics.diagnostics',

                    messages: {
                        notification: function(args) {
                            return 'label.retrievediagnostics.diagnostics';
                        }
                    },

                    createForm: {
                        title: 'label.retrievediagnostics.diagnostics',
                        button: cloudStack.button,
                            label: "label.retrievediagnostics.diagnostics"
                        fields: {
                            systemvmtype: {
                                label: 'label.systemvm.name',
                                converter: function (args) {
                                if (args == "consoleproxy")
                                    return "Console Proxy VM"; else if (args == "secondarystoragevm")
                                    return "Secondary Storage VM"; else
                                    return args;
                                }
                            },
                            diagnosticstype: {
                                label: 'label.diagnosticstype',
                                select: function(args) {
                                    $.ajax({
                                        success: function(json) {
                                            var types = [];
                                            var items = ["LOGFILES", "IPTABLES", "CONFIGURATIONFILES", "IFCONFIG", "PROPERTYFILES", "DHCPFILES", "USERDATA", "LB", "VPN", "IPTABLESretrieve", "IFCONFIGretrieve", "ROUTEretrieve"];
                                            if (items != null) {
                                                for (var i = 0; i < items.length; i++) {
                                                    types.push({
                                                        id: items[i].type,
                                                        //description: items[i].type
                                                    });
                                                }
                                            }
                                            args.response.success({
                                                data: types
                                            })
                                        }
                                    });
                                }
                            },
                            detail: {
                                label: 'label.retrievediagnostics.detail',
                                validation: {
                                    required: false
                                }
                            },
                        }
                    },

                    action: function(args) {
                        var data = {
                            systemvmtype: args.data.systemvmtype,
                            diagnosticstype: args.data.diagnosticstype,
                            detail: args.data.detail
                        };
                        $.ajax({
                            var array1 =[];
                            array1.push("&systemVmId=" + systemvmtype);
                            array1.push("&type=" + diagnosticstype);
                            array1.push("&detail=" + detail);
                            url: createURL('retrieveDiagnostics' + array1.join("")),
                            dataType: "json",
                            async: true,
                            success: function (json) {
                                args.response.success({
                                    data: {
                                    }
                                });
                            },
                            error: function (json) {
                                args.response.error(parseXMLHttpResponse(json));
                            }

                        });
                    },

                    notification: {
                        poll: pollAsyncJobResult
                    }
                }
            },
            detailView: {
                actions: {

                viewAll: {
                    path: 'instances',
                    label: 'label.instances'
                },

                tabs: {
                    details: {
                        title: 'label.details',
                        fields: [{
                            systemvmtype: {
                                label: 'label.systemvm.name'
                            }
                        }, {
                            diagnosticstype: {
                                label: 'label.diagnosticstype'
                            },
                            detail: {
                                label: 'label.retrievediagnostics.detail'
                            },
                        }],

                    }
                }
            }
        }
    };

})(cloudStack);



