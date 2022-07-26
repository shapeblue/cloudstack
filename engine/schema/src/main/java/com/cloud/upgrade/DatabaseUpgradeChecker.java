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
package com.cloud.upgrade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ObjectArrays.concat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;

import javax.inject.Inject;

import org.apache.cloudstack.utils.CloudStackVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.cloud.upgrade.dao.DbUpgrade;
import com.cloud.upgrade.dao.DbUpgradeSystemVmTemplate;
import com.cloud.upgrade.dao.Upgrade217to218;
import com.cloud.upgrade.dao.Upgrade218to22;
import com.cloud.upgrade.dao.Upgrade218to224DomainVlans;
import com.cloud.upgrade.dao.Upgrade2210to2211;
import com.cloud.upgrade.dao.Upgrade2211to2212;
import com.cloud.upgrade.dao.Upgrade2212to2213;
import com.cloud.upgrade.dao.Upgrade2213to2214;
import com.cloud.upgrade.dao.Upgrade2214to30;
import com.cloud.upgrade.dao.Upgrade221to222;
import com.cloud.upgrade.dao.Upgrade222to224;
import com.cloud.upgrade.dao.Upgrade224to225;
import com.cloud.upgrade.dao.Upgrade225to226;
import com.cloud.upgrade.dao.Upgrade227to228;
import com.cloud.upgrade.dao.Upgrade228to229;
import com.cloud.upgrade.dao.Upgrade229to2210;
import com.cloud.upgrade.dao.Upgrade301to302;
import com.cloud.upgrade.dao.Upgrade302to303;
import com.cloud.upgrade.dao.Upgrade302to40;
import com.cloud.upgrade.dao.Upgrade303to304;
import com.cloud.upgrade.dao.Upgrade304to305;
import com.cloud.upgrade.dao.Upgrade305to306;
import com.cloud.upgrade.dao.Upgrade306to307;
import com.cloud.upgrade.dao.Upgrade307to410;
import com.cloud.upgrade.dao.Upgrade30to301;
import com.cloud.upgrade.dao.Upgrade40to41;
import com.cloud.upgrade.dao.Upgrade41000to4100226;
import com.cloud.upgrade.dao.Upgrade4100226to4100227;
import com.cloud.upgrade.dao.Upgrade4100227to4100228;
import com.cloud.upgrade.dao.Upgrade4100228to4100229;
import com.cloud.upgrade.dao.Upgrade4100229to4100230;
import com.cloud.upgrade.dao.Upgrade4100230to4100231;
import com.cloud.upgrade.dao.Upgrade4100231to4100232;
import com.cloud.upgrade.dao.Upgrade4100232to4100233;
import com.cloud.upgrade.dao.Upgrade4100233to4100234;
import com.cloud.upgrade.dao.Upgrade4100234to4100235;
import com.cloud.upgrade.dao.Upgrade4100235to4100236;
import com.cloud.upgrade.dao.Upgrade4100236to4100237;
import com.cloud.upgrade.dao.Upgrade4100237to4100238;
import com.cloud.upgrade.dao.Upgrade4100238to4100239;
import com.cloud.upgrade.dao.Upgrade4100239to4100240;
import com.cloud.upgrade.dao.Upgrade4100240to41100;
import com.cloud.upgrade.dao.Upgrade410to420;
import com.cloud.upgrade.dao.Upgrade41100to41110;
import com.cloud.upgrade.dao.Upgrade41110to41120;
import com.cloud.upgrade.dao.Upgrade41120to41130;
import com.cloud.upgrade.dao.Upgrade41130to41200;
import com.cloud.upgrade.dao.Upgrade41200to41201;
import com.cloud.upgrade.dao.Upgrade41201to41202;
import com.cloud.upgrade.dao.Upgrade41202to41203;
import com.cloud.upgrade.dao.Upgrade41203to41204;
import com.cloud.upgrade.dao.Upgrade41204to41205;
import com.cloud.upgrade.dao.Upgrade41205to41206;
import com.cloud.upgrade.dao.Upgrade41206to41207;
import com.cloud.upgrade.dao.Upgrade41207to41208;
import com.cloud.upgrade.dao.Upgrade41208to41209;
import com.cloud.upgrade.dao.Upgrade41209to412010;
import com.cloud.upgrade.dao.Upgrade41210to412011;
import com.cloud.upgrade.dao.Upgrade41211to412012;
import com.cloud.upgrade.dao.Upgrade41212to412013;
import com.cloud.upgrade.dao.Upgrade412025to41300;
import com.cloud.upgrade.dao.Upgrade41300to41310;
import com.cloud.upgrade.dao.Upgrade41310to41400;
import com.cloud.upgrade.dao.Upgrade41400to41500;
import com.cloud.upgrade.dao.Upgrade41500to41510;
import com.cloud.upgrade.dao.Upgrade41510to41520;
import com.cloud.upgrade.dao.Upgrade41520to41600;
import com.cloud.upgrade.dao.Upgrade41600to41610;
import com.cloud.upgrade.dao.Upgrade41610to41700;
import com.cloud.upgrade.dao.Upgrade41700to41710;
import com.cloud.upgrade.dao.Upgrade41710to41800;
import com.cloud.upgrade.dao.Upgrade420to421;
import com.cloud.upgrade.dao.Upgrade421to430;
import com.cloud.upgrade.dao.Upgrade430to440;
import com.cloud.upgrade.dao.Upgrade431to440;
import com.cloud.upgrade.dao.Upgrade432to440;
import com.cloud.upgrade.dao.Upgrade440to441;
import com.cloud.upgrade.dao.Upgrade441to442;
import com.cloud.upgrade.dao.Upgrade442to450;
import com.cloud.upgrade.dao.Upgrade443to444;
import com.cloud.upgrade.dao.Upgrade444to450;
import com.cloud.upgrade.dao.Upgrade450to451;
import com.cloud.upgrade.dao.Upgrade451to452;
import com.cloud.upgrade.dao.Upgrade452to453;
import com.cloud.upgrade.dao.Upgrade453to460;
import com.cloud.upgrade.dao.Upgrade460to461;
import com.cloud.upgrade.dao.Upgrade461to470;
import com.cloud.upgrade.dao.Upgrade470to471;
import com.cloud.upgrade.dao.Upgrade471to480;
import com.cloud.upgrade.dao.Upgrade480to481;
import com.cloud.upgrade.dao.Upgrade481to490;
import com.cloud.upgrade.dao.Upgrade490to4910;
import com.cloud.upgrade.dao.Upgrade4910to4920;
import com.cloud.upgrade.dao.Upgrade4920to4930;
import com.cloud.upgrade.dao.Upgrade4930to41000;
import com.cloud.upgrade.dao.UpgradeSnapshot217to224;
import com.cloud.upgrade.dao.UpgradeSnapshot223to224;
import com.cloud.upgrade.dao.VersionDao;
import com.cloud.upgrade.dao.VersionDaoImpl;
import com.cloud.upgrade.dao.VersionVO;
import com.cloud.upgrade.dao.VersionVO.Step;
import com.cloud.utils.component.SystemIntegrityChecker;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.ScriptRunner;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.common.annotations.VisibleForTesting;

public class DatabaseUpgradeChecker implements SystemIntegrityChecker {
    private static final Logger s_logger = Logger.getLogger(DatabaseUpgradeChecker.class);
    private final DatabaseVersionHierarchy hierarchy;

    @Inject
    VersionDao _dao;

    public DatabaseUpgradeChecker() {
        _dao = new VersionDaoImpl();

        hierarchy = DatabaseVersionHierarchy.builder()
                // legacy
                .next("2.1.7"   , new Upgrade217to218())
                .next("2.1.7.1" , new UpgradeSnapshot217to224())
                .next("2.1.8"   , new Upgrade218to22())
                .next("2.1.8.1" , new Upgrade218to224DomainVlans())
                .next("2.1.9"   , new Upgrade218to22())
                .next("2.2.1"   , new Upgrade221to222())
                .next("2.2.2"   , new Upgrade222to224())
                .next("2.2.3"   , new Upgrade222to224())
                .next("2.2.3.1" , new UpgradeSnapshot223to224())
                .next("2.2.4"   , new Upgrade224to225())
                .next("2.2.5"   , new Upgrade225to226())
                .next("2.2.6"   , new Upgrade227to228())
                .next("2.2.7"   , new Upgrade227to228())
                .next("2.2.8"   , new Upgrade228to229())
                .next("2.2.9"   , new Upgrade229to2210())
                .next("2.2.10"  , new Upgrade2210to2211())
                .next("2.2.11"  , new Upgrade2211to2212())
                .next("2.2.12"  , new Upgrade2212to2213())
                .next("2.2.13"  , new Upgrade2213to2214())
                .next("2.2.14"  , new Upgrade2214to30())
                .next("2.2.15"  , new Upgrade2214to30())
                .next("2.2.16"  , new Upgrade2214to30())
                .next("3.0.0"   , new Upgrade30to301())
                .next("3.0.1"   , new Upgrade301to302())
                .next("3.0.2"   , new Upgrade302to303())
                .next("3.0.2.1" , new Upgrade302to40())
                .next("3.0.3"   , new Upgrade303to304())
                .next("3.0.4"   , new Upgrade304to305())
                .next("3.0.5"   , new Upgrade305to306())
                .next("3.0.6"   , new Upgrade306to307())
                .next("3.0.7"   , new Upgrade307to410())

                // recent
                .next("4.0.0"   , new Upgrade40to41())
                .next("4.0.1"   , new Upgrade40to41())
                .next("4.0.2"   , new Upgrade40to41())
                .next("4.1.0"   , new Upgrade410to420())
                .next("4.1.1"   , new Upgrade410to420())
                .next("4.2.0"   , new Upgrade420to421())
                .next("4.2.1"   , new Upgrade421to430())
                .next("4.3.0"   , new Upgrade430to440())
                .next("4.3.1"   , new Upgrade431to440())
                .next("4.3.2"   , new Upgrade432to440())
                .next("4.4.0"   , new Upgrade440to441())
                .next("4.4.1"   , new Upgrade441to442())
                .next("4.4.2"   , new Upgrade442to450())
                .next("4.4.3"   , new Upgrade443to444())
                .next("4.4.4"   , new Upgrade444to450())
                .next("4.5.0"   , new Upgrade450to451())
                .next("4.5.1"   , new Upgrade451to452())
                .next("4.5.2"   , new Upgrade452to453())
                .next("4.5.3"   , new Upgrade453to460())
                .next("4.6.0"   , new Upgrade460to461())
                .next("4.6.1"   , new Upgrade461to470())
                .next("4.6.2"   , new Upgrade461to470())
                .next("4.7.0"   , new Upgrade470to471())
                .next("4.7.1"   , new Upgrade471to480())
                .next("4.7.2"   , new Upgrade471to480())
                .next("4.8.0"   , new Upgrade480to481())
                .next("4.8.1"   , new Upgrade481to490())
                .next("4.8.2.0" , new Upgrade481to490())
                .next("4.9.0"   , new Upgrade490to4910())
                .next("4.9.1.0" , new Upgrade4910to4920())
                .next("4.9.2.0" , new Upgrade4920to4930())
                .next("4.9.3.0" , new Upgrade4930to41000())
                .next("4.9.3.1" , new Upgrade4930to41000())
                .next("4.10.0.0", new Upgrade41000to4100226())
                .next("4.10.0.226", new Upgrade4100226to4100227())
                .next("4.10.0.227", new Upgrade4100227to4100228())
                .next("4.10.0.228", new Upgrade4100228to4100229())
                .next("4.10.0.229", new Upgrade4100229to4100230())
                .next("4.10.0.230", new Upgrade4100230to4100231())
                .next("4.10.0.231", new Upgrade4100231to4100232())
                .next("4.10.0.232", new Upgrade4100232to4100233())
                .next("4.10.0.233", new Upgrade4100233to4100234())
                .next("4.10.0.234", new Upgrade4100234to4100235())
                .next("4.10.0.235", new Upgrade4100235to4100236())
                .next("4.10.0.236", new Upgrade4100236to4100237())
                .next("4.10.0.237", new Upgrade4100237to4100238())
                .next("4.10.0.238", new Upgrade4100238to4100239())
                .next("4.10.0.239", new Upgrade4100239to4100240())
                .next("4.10.0.240", new Upgrade4100240to41100())
                .next("4.11.0.0", new Upgrade41100to41110())
                .next("4.11.1.0", new Upgrade41110to41120())
                .next("4.11.2.0", new Upgrade41120to41130())
                .next("4.11.3.0", new Upgrade41130to41200())
                .next("4.12.0.0", new Upgrade41200to41201())
                .next("4.12.0.1", new Upgrade41201to41202())
                .next("4.12.0.2", new Upgrade41202to41203())
                .next("4.12.0.3", new Upgrade41203to41204())
                .next("4.12.0.4", new Upgrade41204to41205())
                .next("4.12.0.5", new Upgrade41205to41206())
                .next("4.12.0.6", new Upgrade41206to41207())
                .next("4.12.0.7", new Upgrade41207to41208())
                .next("4.12.0.8", new Upgrade41208to41209())
                .next("4.12.0.9", new Upgrade41209to412010())
                .next("4.12.0.10", new Upgrade41210to412011())
                .next("4.12.0.11", new Upgrade41211to412012())
                .next("4.12.0.12", new Upgrade41212to412013())
                .next("4.12.0.25", new Upgrade412025to41300())
                .next("4.13.0.0", new Upgrade41300to41310())
                .next("4.13.1.0", new Upgrade41310to41400())
                .next("4.14.0.0", new Upgrade41400to41500())
                .next("4.14.1.0", new Upgrade41400to41500())
                .next("4.15.0.0", new Upgrade41500to41510())
                .next("4.15.1.0", new Upgrade41510to41520())
                .next("4.15.2.0", new Upgrade41520to41600())
                .next("4.16.0.0", new Upgrade41600to41610())
                .next("4.16.1.0", new Upgrade41610to41700())
                .next("4.17.0.0", new Upgrade41700to41710())
                .next("4.17.1.0", new Upgrade41710to41800())
                .build();
    }

    protected void runScript(Connection conn, InputStream file) {

        try (InputStreamReader reader = new InputStreamReader(file)) {
            ScriptRunner runner = new ScriptRunner(conn, false, true);
            runner.runScript(reader);
        } catch (IOException e) {
            s_logger.error("Unable to read upgrade script", e);
            throw new CloudRuntimeException("Unable to read upgrade script", e);
        } catch (SQLException e) {
            s_logger.error("Unable to execute upgrade script", e);
            throw new CloudRuntimeException("Unable to execute upgrade script", e);
        }

    }

    @VisibleForTesting
    DbUpgrade[] calculateUpgradePath(final CloudStackVersion dbVersion, final CloudStackVersion currentVersion) {

        checkArgument(dbVersion != null);
        checkArgument(currentVersion != null);
        checkArgument(currentVersion.compareTo(dbVersion) > 0);

        final DbUpgrade[] upgrades = hierarchy.getPath(dbVersion, currentVersion);

        // When there is no upgrade defined for the target version, we assume that there were no schema changes or
        // data migrations required.  Based on that assumption, we add a noop DbUpgrade to the end of the list ...
        final CloudStackVersion tailVersion = upgrades.length > 0 ? CloudStackVersion.parse(upgrades[upgrades.length - 1].getUpgradedVersion()) : dbVersion;

        if (currentVersion.compareTo(tailVersion) != 0) {
            return concat(upgrades, new NoopDbUpgrade(tailVersion, currentVersion));
        }

        return upgrades;

    }

    private void updateSystemVmTemplates(DbUpgrade[] upgrades) {
        for (int i = upgrades.length - 1; i >= 0; i--) {
            DbUpgrade upgrade = upgrades[i];
            if (upgrade instanceof DbUpgradeSystemVmTemplate) {
                TransactionLegacy txn = TransactionLegacy.open("Upgrade");
                txn.start();
                try {
                    Connection conn;
                    try {
                        conn = txn.getConnection();
                    } catch (SQLException e) {
                        String errorMessage = "Unable to upgrade the database";
                        s_logger.error(errorMessage, e);
                        throw new CloudRuntimeException(errorMessage, e);
                    }
                    ((DbUpgradeSystemVmTemplate)upgrade).updateSystemVmTemplates(conn);
                    txn.commit();
                    break;
                } catch (CloudRuntimeException e) {
                    String errorMessage = "Unable to upgrade the database";
                    s_logger.error(errorMessage, e);
                    throw new CloudRuntimeException(errorMessage, e);
                } finally {
                    txn.close();
                }
            }
        }
    }

    protected void upgrade(CloudStackVersion dbVersion, CloudStackVersion currentVersion) {
        s_logger.info("Database upgrade must be performed from " + dbVersion + " to " + currentVersion);

        final DbUpgrade[] upgrades = calculateUpgradePath(dbVersion, currentVersion);

        for (DbUpgrade upgrade : upgrades) {
            VersionVO version;
            s_logger.debug("Running upgrade " + upgrade.getClass().getSimpleName() + " to upgrade from " + upgrade.getUpgradableVersionRange()[0] + "-" + upgrade
                    .getUpgradableVersionRange()[1] + " to " + upgrade.getUpgradedVersion());
            TransactionLegacy txn = TransactionLegacy.open("Upgrade");
            txn.start();
            try {
                Connection conn;
                try {
                    conn = txn.getConnection();
                } catch (SQLException e) {
                    String errorMessage = "Unable to upgrade the database";
                    s_logger.error(errorMessage, e);
                    throw new CloudRuntimeException(errorMessage, e);
                }
                InputStream[] scripts = upgrade.getPrepareScripts();
                if (scripts != null) {
                    for (InputStream script : scripts) {
                        runScript(conn, script);
                    }
                }

                upgrade.performDataMigration(conn);

                version = new VersionVO(upgrade.getUpgradedVersion());
                version = _dao.persist(version);

                txn.commit();
            } catch (CloudRuntimeException e) {
                String errorMessage = "Unable to upgrade the database";
                s_logger.error(errorMessage, e);
                throw new CloudRuntimeException(errorMessage, e);
            } finally {
                txn.close();
            }

            // Run the corresponding '-cleanup.sql' script
            txn = TransactionLegacy.open("Cleanup");
            try {
                s_logger.info("Cleanup upgrade " + upgrade.getClass().getSimpleName() + " to upgrade from " + upgrade.getUpgradableVersionRange()[0] + "-" + upgrade
                        .getUpgradableVersionRange()[1] + " to " + upgrade.getUpgradedVersion());

                txn.start();
                Connection conn;
                try {
                    conn = txn.getConnection();
                } catch (SQLException e) {
                    s_logger.error("Unable to cleanup the database", e);
                    throw new CloudRuntimeException("Unable to cleanup the database", e);
                }

                InputStream[] scripts = upgrade.getCleanupScripts();
                if (scripts != null) {
                    for (InputStream script : scripts) {
                        runScript(conn, script);
                        s_logger.debug("Cleanup script " + upgrade.getClass().getSimpleName() + " is executed successfully");
                    }
                }
                txn.commit();

                txn.start();
                version.setStep(Step.Complete);
                version.setUpdated(new Date());
                _dao.update(version.getId(), version);
                txn.commit();
                s_logger.debug("Upgrade completed for version " + version.getVersion());
            } finally {
                txn.close();
            }
        }
        updateSystemVmTemplates(upgrades);
    }

    @Override
    public void check() {
        GlobalLock lock = GlobalLock.getInternLock("DatabaseUpgrade");
        try {
            s_logger.info("Grabbing lock to check for database upgrade.");
            if (!lock.lock(20 * 60)) {
                throw new CloudRuntimeException("Unable to acquire lock to check for database integrity.");
            }

            try {

                final CloudStackVersion dbVersion = CloudStackVersion.parse(_dao.getCurrentVersion());
                final String currentVersionValue = this.getClass().getPackage().getImplementationVersion();

                if (StringUtils.isBlank(currentVersionValue)) {
                    return;
                }

                String csVersion = SystemVmTemplateRegistration.parseMetadataFile();
                final CloudStackVersion sysVmVersion = CloudStackVersion.parse(csVersion);
                final  CloudStackVersion currentVersion = CloudStackVersion.parse(currentVersionValue);
                SystemVmTemplateRegistration.CS_MAJOR_VERSION  = String.valueOf(sysVmVersion.getMajorRelease()) + "." + String.valueOf(sysVmVersion.getMinorRelease());
                SystemVmTemplateRegistration.CS_TINY_VERSION = String.valueOf(sysVmVersion.getPatchRelease());

                s_logger.info("DB version = " + dbVersion + " Code Version = " + currentVersion);

                if (dbVersion.compareTo(currentVersion) > 0) {
                    throw new CloudRuntimeException("Database version " + dbVersion + " is higher than management software version " + currentVersionValue);
                }

                if (dbVersion.compareTo(currentVersion) == 0) {
                    s_logger.info("DB version and code version matches so no upgrade needed.");
                    return;
                }

                upgrade(dbVersion, currentVersion);
            } finally {
                lock.unlock();
            }
        } finally {
            lock.releaseRef();
        }
    }

    @VisibleForTesting
    protected static final class NoopDbUpgrade implements DbUpgrade {

        private final String upgradedVersion;
        private final String[] upgradeRange;

        private NoopDbUpgrade(final CloudStackVersion fromVersion, final CloudStackVersion toVersion) {

            super();

            upgradedVersion = toVersion.toString();
            upgradeRange = new String[] {fromVersion.toString(), toVersion.toString()};

        }

        @Override
        public String[] getUpgradableVersionRange() {
            return Arrays.copyOf(upgradeRange, upgradeRange.length);
        }

        @Override
        public String getUpgradedVersion() {
            return upgradedVersion;
        }

        @Override
        public boolean supportsRollingUpgrade() {
            return false;
        }

        @Override
        public InputStream[] getPrepareScripts() {
            return new InputStream[0];
        }

        @Override
        public void performDataMigration(Connection conn) {

        }

        @Override
        public InputStream[] getCleanupScripts() {
            return new InputStream[0];
        }

    }
}
