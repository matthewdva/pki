//
// Copyright Red Hat, Inc.
//
// SPDX-License-Identifier: GPL-2.0-or-later
//
package org.dogtagpki.server.cli;

import java.math.BigInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.dogtagpki.cli.CLI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netscape.cmscore.apps.DatabaseConfig;
import com.netscape.cmscore.apps.EngineConfig;
import com.netscape.cmscore.ldapconn.LDAPConfig;
import com.netscape.cmscore.ldapconn.LDAPConnectionConfig;
import com.netscape.cmscore.ldapconn.LdapAuthInfo;
import com.netscape.cmscore.ldapconn.LdapBoundConnection;
import com.netscape.cmscore.ldapconn.LdapConnInfo;
import com.netscape.cmscore.ldapconn.PKISocketConfig;
import com.netscape.cmscore.ldapconn.PKISocketFactory;
import com.netscape.cmsutil.password.IPasswordStore;
import com.netscape.cmsutil.password.PasswordStoreConfig;

import netscape.ldap.LDAPAttribute;
import netscape.ldap.LDAPModification;

/**
 * @author Endi S. Dewata
 */
public class SubsystemRangeUpdateCLI extends SubsystemCLI {

    public static Logger logger = LoggerFactory.getLogger(SubsystemRangeUpdateCLI.class);

    public SubsystemRangeUpdateCLI(CLI parent) {
        super("update", "Update " + parent.getParent().getName().toUpperCase() + " ranges", parent);
    }

    @Override
    public void createOptions() {

        Option option = new Option("d", true, "NSS database location");
        option.setArgName("database");
        options.addOption(option);

        option = new Option("f", true, "NSS database password configuration");
        option.setArgName("password config");
        options.addOption(option);
    }

    @Override
    public void execute(CommandLine cmd) throws Exception {

        initializeTomcatJSS();
        String subsystem = parent.getParent().getName();
        EngineConfig cs = getEngineConfig(subsystem);
        cs.load();

        LDAPConfig ldapConfig = cs.getInternalDBConfig();
        String baseDN = ldapConfig.getBaseDN();

        PasswordStoreConfig psc = cs.getPasswordStoreConfig();
        IPasswordStore passwordStore = IPasswordStore.create(psc);

        LDAPConnectionConfig connConfig = ldapConfig.getConnectionConfig();

        LdapConnInfo connInfo = new LdapConnInfo(connConfig);
        LdapAuthInfo authInfo = getAuthInfo(passwordStore, connInfo, ldapConfig);

        PKISocketConfig socketConfig = cs.getSocketConfig();

        PKISocketFactory socketFactory;
        if (authInfo.getAuthType() == LdapAuthInfo.LDAP_AUTHTYPE_SSLCLIENTAUTH) {
            socketFactory = new PKISocketFactory(authInfo.getClientCertNickname());
        } else {
            socketFactory = new PKISocketFactory(connInfo.getSecure());
        }
        socketFactory.init(socketConfig);

        LdapBoundConnection conn = new LdapBoundConnection(socketFactory, connInfo, authInfo);

        DatabaseConfig dbConfig = cs.getDatabaseConfig();

        try {
            logger.info("Updating serial number range");

            BigInteger endSerialNumber = new BigInteger(dbConfig.getEndSerialNumber());
            BigInteger nextSerialNumber = endSerialNumber.add(BigInteger.ONE);

            String serialDN = dbConfig.getSerialDN() + "," + baseDN;
            LDAPAttribute attrSerialNextRange = new LDAPAttribute("nextRange", nextSerialNumber.toString());
            LDAPModification serialmod = new LDAPModification(LDAPModification.REPLACE, attrSerialNextRange);

            conn.modify(serialDN, serialmod);

            logger.info("Updating request number range");

            BigInteger endRequestNumber = new BigInteger(dbConfig.getEndRequestNumber());
            BigInteger nextRequestNumber = endRequestNumber.add(BigInteger.ONE);

            String requestDN = dbConfig.getRequestDN() + "," + baseDN;
            LDAPAttribute attrRequestNextRange = new LDAPAttribute("nextRange", nextRequestNumber.toString());
            LDAPModification requestmod = new LDAPModification(LDAPModification.REPLACE, attrRequestNextRange);

            conn.modify(requestDN, requestmod);

        } finally {
            conn.disconnect();
        }
    }
}
