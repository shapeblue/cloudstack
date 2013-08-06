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
package org.apache.cloudstack.framework.config;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.framework.config.dao.ConfigurationDao;

import com.cloud.utils.db.EntityManager;

/**
 * DepotImpl implements the ConfigDepot interface
 *
 */
class ConfigDepotImpl implements ConfigDepot, ConfigDepotAdmin {
    @Inject
    EntityManager _entityMgr;
    
    @Inject
    ConfigurationDao _configDao;
    
    @Inject
    List<Configurable> _configurables;

    public ConfigDepotImpl() {
    }

    @Override
    public <T> ConfigValue<T> get(ConfigKey<T> config) {
        return new ConfigValue<T>(_entityMgr, config);
    }
    
    @Override
    public void populateConfigurations() {
        Date date = new Date();
        for (Configurable configurable : _configurables) {
            for (ConfigKey<?> key : configurable.getConfigKeys()) {
                ConfigurationVO vo = _configDao.findById(key.key());
                if (vo == null) {
                    vo = new ConfigurationVO(configurable.getConfigComponentName(), key);
                    vo.setUpdated(date);
                    _configDao.persist(vo);
                } else {
                    if (vo.isDynamic() != key.isDynamic() ||
                            !vo.getDescription().equals(key.description()) ||
                            !vo.getDefaultValue().equals(key.defaultValue())) {
                        vo.setDynamic(key.isDynamic());
                        vo.setDescription(key.description());
                        vo.setDefaultValue(key.defaultValue());
                        vo.setUpdated(date);
                        _configDao.persist(vo);
                    }
                }
            }

            // TODO: Missing code to remove the updated field if the a configurationVO's name cannot be found any more.
        }
    }

    @Override
    public List<String> getComponentsInDepot() {
        return new ArrayList<String>();
    }
}
