// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.cmscore.ldap;

import org.dogtagpki.server.ca.CAEngine;
import org.dogtagpki.server.ca.CAEngineConfig;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.ldap.ELdapException;
import com.netscape.certsrv.ldap.ILdapConnFactory;
import com.netscape.certsrv.ldap.ILdapConnModule;
import com.netscape.cmscore.base.ConfigStore;
import com.netscape.cmscore.ldapconn.LDAPAuthenticationConfig;
import com.netscape.cmscore.ldapconn.LDAPConfig;
import com.netscape.cmscore.ldapconn.LDAPConnectionConfig;
import com.netscape.cmscore.ldapconn.LdapAuthInfo;
import com.netscape.cmscore.ldapconn.LdapBoundConnFactory;
import com.netscape.cmscore.ldapconn.LdapConnInfo;
import com.netscape.cmscore.ldapconn.PKISocketConfig;
import com.netscape.cmsutil.password.IPasswordStore;

import netscape.ldap.LDAPConnection;

public class LdapConnModule implements ILdapConnModule {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LdapConnModule.class);

    protected ConfigStore mConfig;
    protected LdapBoundConnFactory mLdapConnFactory = null;
    private boolean mInited = false;

    /**
     * instantiate connection factory.
     */

    public LdapConnModule() {
    }

    public LdapConnModule(LdapBoundConnFactory factory) {
        mLdapConnFactory = factory;
        mInited = true;
    }

    @Override
    public void init(ConfigStore config) throws EBaseException {

        logger.debug("LdapConnModule: init called");
        if (mInited) {
            logger.debug("LdapConnModule: already initialized. return.");
            return;
        }

        logger.debug("LdapConnModule: init begins");

        CAEngine engine = CAEngine.getInstance();
        CAEngineConfig cs = engine.getConfig();

        PKISocketConfig socketConfig = cs.getSocketConfig();
        IPasswordStore passwordStore = engine.getPasswordStore();

        mConfig = config;
        /*
        mLdapConnFactory = new LdapBoundConnFactory();
        mLdapConnFactory.init(mConfig.getSubStore("ldap"));
        */

        // support publishing dirsrv with different pwd than internaldb
        LDAPConfig ldap = mConfig.getSubStore("ldap", LDAPConfig.class);

        LDAPConnectionConfig connConfig = ldap.getConnectionConfig();
        LdapConnInfo connInfo = new LdapConnInfo(connConfig);

        LDAPAuthenticationConfig authConfig = ldap.getAuthenticationConfig();

        LdapAuthInfo authInfo = new LdapAuthInfo();
        authInfo.setPasswordStore(passwordStore);
        authInfo.init(
                authConfig,
                connConfig.getString("host"),
                connConfig.getInteger("port"),
                connInfo.getSecure());

        int minConns = mConfig.getInteger(LdapBoundConnFactory.PROP_MINCONNS, 3);
        int maxConns = mConfig.getInteger(LdapBoundConnFactory.PROP_MAXCONNS, 15);
        // must get authInfo from the config, don't default to internaldb!!!

        logger.debug("Creating LdapBoundConnFactory for LdapConnModule.");
        mLdapConnFactory = new LdapBoundConnFactory("LDAPConnModule", minConns, maxConns, connInfo, authInfo);
        mLdapConnFactory.init(socketConfig, passwordStore);

        mInited = true;

        logger.debug("LdapConnModule: init ends");
    }

    /**
     * Returns the internal ldap connection factory.
     * This can be useful to get a ldap connection to the
     * ldap publishing directory without having to get it again from the
     * config file. Note that this means sharing a ldap connection pool
     * with the ldap publishing module so be sure to return connections to pool.
     * Use ILdapConnFactory.getConn() to get a Ldap connection to the ldap
     * publishing directory.
     * Use ILdapConnFactory.returnConn() to return the connection.
     *
     * @see com.netscape.cmscore.ldapconn.LdapBoundConnFactory
     * @see com.netscape.certsrv.ldap.ILdapConnFactory
     */
    @Override
    public ILdapConnFactory getLdapConnFactory() {
        return mLdapConnFactory;
    }

    public LDAPConnection getConn() throws ELdapException {
        return mLdapConnFactory.getConn();
    }

    public void returnConn(LDAPConnection conn) throws ELdapException {
        mLdapConnFactory.returnConn(conn);
    }
}
