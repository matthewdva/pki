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
package com.netscape.cmscore.connector;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.connector.IPKIMessage;
import com.netscape.certsrv.logging.SignedAuditEvent;
import com.netscape.certsrv.logging.event.ClientAccessSessionEstablishEvent;
import com.netscape.cms.logging.SignedAuditLogger;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmsutil.http.HttpClient;
import com.netscape.cmsutil.http.HttpRequest;
import com.netscape.cmsutil.http.HttpResponse;
import com.netscape.cmsutil.net.ISocketFactory;

/**
 * This represents a HTTP connection to a remote authority.
 * Http connection is used by the connector to send
 * PKI messages to a remote authority. The remote authority
 * will reply with a PKI message as well. An example would
 * be the communication between a CA and a KRA.
 */
public class HttpConnection {

    private static Logger logger = LoggerFactory.getLogger(HttpConnection.class);
    private static SignedAuditLogger signedAuditLogger = SignedAuditLogger.getLogger();

    protected RemoteAuthority mDest;
    protected HttpRequest mHttpreq = new HttpRequest();
    protected HttpRequestEncoder mReqEncoder;
    protected HttpClient mHttpClient = null;

    int timeout = 0;
    List<InetSocketAddress> targets;
    String localIP = "localhost";

    public HttpConnection(RemoteAuthority dest, ISocketFactory factory,
            int timeout // seconds
            ) {

        logger.debug("HttpConnection: Creating HttpConnection with timeout=" + timeout);
        try {
            localIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            // default to "localhost";
        }

        mDest = dest;
        mReqEncoder = new HttpRequestEncoder();
        mHttpClient = new HttpClient(factory);

        this.timeout = timeout;

        targets = parseTarget(dest.getHost(), dest.getPort());

        try {
            mHttpreq.setMethod("POST");

            // in case of multi-uri, uri will be set right before send
            //   by calling setRequestURI(uri)
            if (mDest.getURI() != null)
                mHttpreq.setURI(mDest.getURI());

            String contentType = dest.getContentType();
            if (contentType != null) {
                logger.debug("HttpConnection: setting Content-Type");
                mHttpreq.setHeader("Content-Type", contentType );
            }

            mHttpreq.setHeader("Connection", "Keep-Alive");

            connect();

        } catch (IOException e) {
            // server's probably down. that's fine. try later.
            logger.warn("HttpConnection: Unable to create connection: " + e.getMessage(), e);
        }
    }

    public HttpConnection(RemoteAuthority dest, ISocketFactory factory) {
        this(dest, factory, 0);
    }

    List<InetSocketAddress> parseTarget(String target, int port) {

        List<InetSocketAddress> results = new ArrayList<>();

        if (target == null || target.indexOf(' ') < 0) {
            // target is a single hostname

            // add hostname and the global port to the results
            results.add(new InetSocketAddress(target, port));
            return results;
        }

        // target is a list of hostname:port, for example:
        // "server1.example.com:8443 server2.example.com:8443"

        for (String hostnamePort : target.split(" ")) {

            // parse hostname and port, and ignore the global port
            String[] parts = hostnamePort.split(":");
            String hostname = parts[0];
            port = Integer.parseInt(parts[1]);

            // add hostname and port to the results
            results.add(new InetSocketAddress(hostname, port));
        }

        return results;
    }

    void connect() throws IOException {

        IOException exception = null;
        SignedAuditEvent auditEvent;

        // try all targets
        for (InetSocketAddress target : targets) {

            String hostname = target.getHostString();
            int port = target.getPort();

            try {
                logger.debug("HttpConnection: Connecting to " + hostname + ":" + port + " with timeout " + timeout + "s");

                mHttpClient.connect(hostname, port, timeout * 1000);

                logger.debug("HttpConnection: Connected to " + hostname + ":" + port);
                return;

            } catch (IOException e) {
                exception = e;
                logger.warn("HttpConnection: Unable to connect to " + hostname + ":" + port + ": " + e.getMessage(), e);
                auditEvent = ClientAccessSessionEstablishEvent.createFailureEvent(
                        localIP,
                        hostname,
                        Integer.toString(port),
                        "SYSTEM",
                        "connect:" +e.toString());
                signedAuditLogger.log(auditEvent);

                // try the next target immediately
            }
        }

        // throw the last exception
        throw exception;
    }

    /**
     * Sets the HttpRequest URI before send
     *
     * @param uri the uri for the HttpRequest
     */
    public void setRequestURI(String uri)
            throws EBaseException {
        mHttpreq.setURI(uri);
    }

    /**
     * Gets the HttpRequest URI
     */
    public String getRequestURI() {
        return mHttpreq.getURI();
    }

    /**
     * Sends the PKI message to the remote authority.
     *
     * @param tomsg Message to forward to authority.
     * @exception EBaseException Failed to send message.
     */
    public IPKIMessage send(IPKIMessage tomsg) throws EBaseException {

        String url = "https://" + mDest.getHost() + ":" + mDest.getPort() + mHttpreq.getURI();
        logger.info("HttpConnection: Sending request to " + url);

        IPKIMessage replymsg = null;
        HttpResponse resp = null;

        logger.debug("in HttpConnection.send " + this);
        logger.trace("encoding request ");

        String content = null;

        try {
            content = mReqEncoder.encode(tomsg);
        } catch (IOException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_ATTRIBUTE", "Could not encode request"));
        }
        logger.trace("encoded request");
        logger.trace("------ " + content.length() + "-----");
        logger.trace(content);
        logger.trace("--------------------------");
        resp = doSend(content);

        // decode reply.
        // if reply is bad, error is thrown and request will be resent
        String pcontent = resp.getContent();

        logger.trace("Server returned");
        logger.trace("-------");
        logger.trace(pcontent);
        logger.trace("-------");
        //logger.debug("HttpConnection.send response: " + pcontent);
        if (pcontent != null && !pcontent.equals(""))
            logger.debug("HttpConnection.send response: got content");
        else
            logger.debug("HttpConnection.send response: null or empty content");

        try {
            replymsg = (IPKIMessage) mReqEncoder.decode(pcontent);
        } catch (IOException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_ATTRIBUTE", "Could not decode content"));
        }
        logger.debug("HttpConn:decoded reply");
        return replymsg;
    }

    /**
     * Sends the message to the remote authority.
     *
     * @param content Message to forward to authority.
     * @return HttpResponse response to be parsed by the client
     * @exception EBaseException Failed to send message.
     */
    public HttpResponse send(String content) throws EBaseException {

        String url = "https://" + mDest.getHost() + ":" + mDest.getPort() + mHttpreq.getURI();
        logger.info("HttpConnection: Sending request to " + url);

        // cfu: multi-uri support
        HttpResponse resp = null;
        if ((content == null) || content.equals("")) {
            logger.error("HttpConnection.send: with String content: null or empty");
            throw new EBaseException("HttpConnection.send: with String content: null or empty");
        }

        //logger.debug("HttpConnection.send: with String content: " + content);

        resp = doSend(content);
        return resp;
    }

    private HttpResponse doSend(String content) throws EBaseException {

        HttpResponse resp = null;
        boolean reconnected = false;
        SignedAuditEvent auditEvent;
        String localIP = "localhost";
        try {
            localIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            // default to "localhost";
        }

        if (getRequestURI() == null) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_ATTRIBUTE", "URI not set in HttpRequest"));
        }

        mHttpreq.setHeader("Content-Length",
                Integer.toString(content.length()));
        logger.debug("HttpConnection.doSend: with String content length: " + Integer.toString(content.length()));
        mHttpreq.setContent(content);

        try {
            if (!mHttpClient.connected()) {
                connect();
                reconnected = true;
            }

        } catch (IOException e) {

            logger.error("Unable to send HTTP request: " + e.getMessage(), e);

            if (e.getMessage().indexOf("Peer's certificate issuer has been marked as not trusted") != -1) {
                throw new EBaseException(
                        CMS.getUserMessage(
                                "CMS_BASE_CONN_FAILED",
                                "(This local authority cannot connect to the remote authority. The local authority's signing certificate must chain to a CA certificate trusted for client authentication in the certificate database. Use the certificate manager, or command line tool such as certutil to verify that the trust permissions of the local authority's issuer cert have 'CT' setting in the SSL client auth field.)"));
            }

            throw new EBaseException(CMS.getUserMessage("CMS_BASE_CONN_FAILED", "Couldn't reconnect " + e));
        }

        // if remote closed connection want to reconnect and resend.
        while (resp == null) {
            try {
                logger.debug("HttpConnection.doSend: sending request");
                resp = mHttpClient.send(mHttpreq);

            } catch (IOException e) {
                logger.error("Unable to send HTTP request: " + e.getMessage(), e);

                auditEvent = ClientAccessSessionEstablishEvent.createFailureEvent(
                        localIP,
                        mHttpClient.getHost(),
                        mHttpClient.getPort(),
                        "SYSTEM",
                        "send:" +e.toString());
                signedAuditLogger.log(auditEvent);

                if (reconnected) {
                    logger.error("HttpConnection.doSend: resend failed again.");
                    throw new EBaseException(
                            CMS.getUserMessage("CMS_BASE_CONN_FAILED", "resend failed again: " + e), e);
                }

                try {
                    logger.warn("HttpConnection.doSend: trying a reconnect ");
                    connect();

                } catch (IOException ex) {
                    logger.error("HttpConnection.doSend: reconnect for resend failed: " + ex.getMessage(), ex);
                    throw new EBaseException(
                            CMS.getUserMessage("CMS_BASE_CONN_FAILED", "reconnect for resend failed: " + ex), e);
                }

                reconnected = true;
            }
        } //while

        // got reply; check status
        String statusStr = resp.getStatusCode();

        logger.debug("HttpConnection.doSend: server returned status " + statusStr);
        int statuscode = -1;

        try {
            statuscode = Integer.parseInt(statusStr);
        } catch (NumberFormatException e) {
            statuscode = -1;
        }

        /* HttpServletResponse.SC_OK = 200 */
        if (statuscode != 200) {

            /* HttpServletResponse.SC_UNAUTHORIZED = 401 */
            if (statuscode == 401) {
                // XXX what to do here.
                String msg = "request no good " + statuscode + " " + resp.getReasonPhrase();

                logger.error("HttpConnection: " + msg);
                throw new EBaseException(CMS.getUserMessage("CMS_BASE_AUTHENTICATE_FAILED", msg));

            } else {
                // XXX what to do here.
                String msg = "HttpConnection: request no good " + statuscode + " " + resp.getReasonPhrase();

                logger.error(msg);
                throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_ATTRIBUTE", msg));
            }
        }

        return resp;
    }
}
