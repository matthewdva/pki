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
package com.netscape.cms.notification;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Vector;

import com.netscape.certsrv.notification.ENotificationException;
import com.netscape.certsrv.notification.IMailNotification;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.apps.CMSEngine;
import com.netscape.cmscore.apps.EngineConfig;
import com.netscape.cmscore.base.ConfigStore;

import netscape.net.smtp.SmtpClient;

/**
 * This class handles mail notification via SMTP.
 * This class uses <b>smtp.host</b> in the configuration for smtp
 * host. The port default (25) is used. If no smtp specified, local
 * host is used
 *
 * @version $Revision$, $Date$
 */
public class MailNotification implements IMailNotification {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MailNotification.class);
    protected final static String PROP_SMTP_SUBSTORE = "smtp";
    protected final static String PROP_HOST = "host";

    private String mHost = null;

    private String mFrom = null;
    private String mTo = null;
    private String mSubject = null;
    private String mContent = null;
    private String mContentType = null;

    public MailNotification() {
        if (mHost == null) {
            try {
                CMSEngine engine = CMS.getCMSEngine();
                EngineConfig config = engine.getConfig();
                ConfigStore c = config.getSubStore(PROP_SMTP_SUBSTORE, ConfigStore.class);

                if (c == null) {
                    return;
                }
                mHost = c.getString(PROP_HOST);

                // log it
                if (mHost !=null) {
               	    String msg =" using external SMTP host: "+mHost;
               	    logger.debug("MailNotification: "  + msg);
                }
            } catch (Exception e) {
                // don't care
            }
        }
    }

    /**
     * send one message to one or more addressees
     */
    @Override
    public void sendNotification() throws IOException, ENotificationException {
        // create smtp client
        SmtpClient sc = null;

        if (mHost!=null && !mHost.equals("")) {
            logger.debug("MailNotification: sendNotification: host="  + mHost);
            sc = new SmtpClient(mHost);
        } else {
            logger.debug("MailNotification: sendNotification: host not set");
            sc = new SmtpClient();
        }

        // set "from", message subject
        if ((mFrom != null) && (!mFrom.equals("")))
            sc.from(mFrom);
        else {
            logger.error("MailNotification.sendNotification: missing sender");
            throw new ENotificationException(
                    CMS.getUserMessage("CMS_NOTIFICATION_NO_SMTP_SENDER"));
        }

        // set "to"
        if ((mTo != null) && (!mTo.equals(""))) {
            logger.info("MailNotification: mail to be sent to " + mTo);
            sc.to(mTo);
        } else {
            logger.error("MailNotification.sendNotification: missing receiver");
            throw new ENotificationException(
                    CMS.getUserMessage("CMS_NOTIFICATION_NO_SMTP_RECEIVER"));
        }

        // set message content
        PrintStream msgStream = sc.startMessage();

        if (mContentType != null) {
            msgStream.print("From: " + mFrom + "\n");
            msgStream.print("MIME-Version: 1.0\n");
            msgStream.print("To: " + mTo + "\n");
            msgStream.print(mSubject + "\n");
            msgStream.print(mContentType + "\n");
        } else {
            msgStream.print("From: " + mFrom + "\n");
            msgStream.print("To: " + mTo + "\n");
            msgStream.print(mSubject + "\n");
        }
        msgStream.print("\r\n");
        msgStream.print(mContent + "\r\n");

        // send
        try {
            sc.closeServer();
            logger.debug("MailNotification.sendNotification: after closeServer");
        } catch (IOException e) {
            logger.error("MailNotification: Unable to send: " + e.getMessage(), e);
            throw new ENotificationException(
                    CMS.getUserMessage("CMS_NOTIFICATION_SMTP_SEND_FAILED", mTo));
        }
    }

    /**
     * sets the "From" field
     *
     * @param from email address of the sender
     */
    @Override
    public void setFrom(String from) {
        mFrom = from;
    }

    /**
     * sets the "Subject" field
     *
     * @param subject subject of the email
     */
    @Override
    public void setSubject(String subject) {
        mSubject = "Subject: " + subject;
    }

    /**
     * sets the "Content-Type" field
     *
     * @param contentType content type of the email
     */
    @Override
    public void setContentType(String contentType) {
        mContentType = "Content-Type: " + contentType;
    }

    /**
     * sets the content of the email
     *
     * @param content the message content
     */
    @Override
    public void setContent(String content) {
        mContent = content;
    }

    /**
     * sets the recipients' email addresses
     *
     * @param addresses a list of email addresses of the recipients
     */
    @Override
    public void setTo(Vector<String> addresses) {
        // concatenate addresses into comma separated mTo String

    }

    /**
     * sets the recipient's email address
     *
     * @param to address of the recipient email address
     */
    @Override
    public void setTo(String to) {
        mTo = to;
    }
}
