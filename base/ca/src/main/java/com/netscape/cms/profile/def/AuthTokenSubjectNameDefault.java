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
package com.netscape.cms.profile.def;

import java.io.IOException;
import java.util.Locale;

import org.mozilla.jss.netscape.security.x509.CertificateSubjectName;
import org.mozilla.jss.netscape.security.x509.X500Name;
import org.mozilla.jss.netscape.security.x509.X509CertInfo;

import com.netscape.certsrv.profile.EProfileException;
import com.netscape.certsrv.property.Descriptor;
import com.netscape.certsrv.property.EPropertyException;
import com.netscape.certsrv.property.IDescriptor;
import com.netscape.cms.profile.ProfileAuthenticator;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.request.Request;

/**
 * This class implements an enrollment default policy that
 * populates subject name based on the attribute values
 * in the authentication token (AuthToken) object.
 *
 * @version $Revision$, $Date$
 */
public class AuthTokenSubjectNameDefault extends EnrollDefault {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthTokenSubjectNameDefault.class);

    public static final String VAL_NAME = "name";

    public AuthTokenSubjectNameDefault() {
        super();
        addValueName(VAL_NAME);
    }

    @Override
    public IDescriptor getValueDescriptor(Locale locale, String name) {
        if (name.equals(VAL_NAME)) {
            return new Descriptor(IDescriptor.STRING, null, null,
                    CMS.getUserMessage(locale, "CMS_PROFILE_SUBJECT_NAME"));
        } else {
            return null;
        }
    }

    @Override
    public void setValue(String name, Locale locale,
            X509CertInfo info, String value)
            throws EPropertyException {
        logger.debug("AuthTokenSubjectNameDefault: begins");
        if (name == null) {
            throw new EPropertyException(CMS.getUserMessage(locale,
                        "CMS_INVALID_PROPERTY", name));
        }
        if (name.equals(VAL_NAME)) {
            X500Name x500name = null;

            try {
                x500name = new X500Name(value);
                logger.debug("AuthTokenSubjectNameDefault: setValue x500name=" + x500name);
            } catch (IOException e) {
                logger.warn("AuthTokenSubjectNameDefault: setValue " + e.getMessage(), e);
                // failed to build x500 name
            }
            logger.debug("AuthTokenSubjectNameDefault: setValue name=" + x500name);
            try {
                info.set(X509CertInfo.SUBJECT,
                        new CertificateSubjectName(x500name));
            } catch (Exception e) {
                // failed to insert subject name
                logger.warn("AuthTokenSubjectNameDefault: setValue " + e.getMessage(), e);
            }
        } else {
            throw new EPropertyException(CMS.getUserMessage(locale,
                        "CMS_INVALID_PROPERTY", name));
        }
    }

    @Override
    public String getValue(String name, Locale locale,
            X509CertInfo info)
            throws EPropertyException {
        if (name == null)
            throw new EPropertyException("Invalid name " + name);
        if (name.equals(VAL_NAME)) {
            CertificateSubjectName sn = null;

            try {
                sn = (CertificateSubjectName)
                        info.get(X509CertInfo.SUBJECT);
                return sn.toString();
            } catch (Exception e) {
                // nothing
                logger.warn("AuthTokenSubjectNameDefault: getValue " + e.getMessage(), e);
            }
            throw new EPropertyException(CMS.getUserMessage(locale,
                        "CMS_INVALID_PROPERTY", name));
        } else {
            throw new EPropertyException(CMS.getUserMessage(locale,
                        "CMS_INVALID_PROPERTY", name));
        }
    }

    @Override
    public String getText(Locale locale) {
        return CMS.getUserMessage(locale,
                "CMS_PROFILE_DEF_AUTHTOKEN_SUBJECT_NAME");
    }

    /**
     * Populates the request with this policy default.
     */
    @Override
    public void populate(Request request, X509CertInfo info)
            throws EProfileException {

        // authenticate the subject name and populate it
        // to the certinfo
        try {
            X500Name name = new X500Name(
                    request.getExtDataInString(ProfileAuthenticator.AUTHENTICATED_NAME));

            logger.debug("AuthTokenSubjectNameDefault: X500Name=" + name.getName());
            info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(name));
        } catch (Exception e) {
            // failed to insert subject name
            logger.error("AuthTokenSubjectNameDefault: " + e.getMessage(), e);
            throw new EProfileException(CMS.getUserMessage(getLocale(request),
                        "CMS_PROFILE_SUBJECT_NAME_NOT_FOUND"));
        }
    }
}
