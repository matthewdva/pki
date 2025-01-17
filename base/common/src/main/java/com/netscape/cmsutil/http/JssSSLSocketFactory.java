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
package com.netscape.cmsutil.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.mozilla.jss.CryptoManager;
import org.mozilla.jss.ssl.SSLCertificateApprovalCallback;
import org.mozilla.jss.ssl.SSLClientCertificateSelectionCallback;
import org.mozilla.jss.ssl.SSLHandshakeCompletedEvent;
import org.mozilla.jss.ssl.SSLHandshakeCompletedListener;
import org.mozilla.jss.ssl.SSLSocket;
import org.mozilla.jss.ssl.SSLSocketListener;

import com.netscape.cmsutil.crypto.CryptoUtil;
import com.netscape.cmsutil.net.ISocketFactory;

/**
 * Uses NSS ssl socket.
 *
 * @version $Revision$ $Date$
 */
public class JssSSLSocketFactory implements ISocketFactory {
    private String mClientAuthCertNickname = null;
    private String mClientCiphers = null;
    private SSLSocket s = null;
    private SSLSocketListener sockListener = null;

    public JssSSLSocketFactory() {
    }

    public JssSSLSocketFactory(String certNickname) {
        mClientAuthCertNickname = certNickname;
    }

    public JssSSLSocketFactory(String certNickname, String ciphers) {
        if (certNickname != null)
            mClientAuthCertNickname = certNickname;

        /*
         * about ciphers
         * Let it inherit default settings (could have been previously set
         * in CS.cfg tcp.clientCiphers by PKISocketFactory)
         * unless it's overwritten by config in respective CS.cfg:
         *
         * CA->KRA: ca.connector.KRA.clientCiphers
         * TPS->KRA/CA/TKS: tps.connector.<ca|kra|tks id>.clientCiphers
         *
         * example for RSA CA, in CS.cfg
         * ca.connector.KRA.clientCiphers=TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
         *
         * example for ECC CA, in CS.cfg
         * ca.connector.KRA.clientCiphers=TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384
         */
        if (ciphers != null) {
            mClientCiphers = ciphers.trim();
            try {
                if (!mClientCiphers.isEmpty())
                    CryptoUtil.setClientCiphers(mClientCiphers);
            } catch (Exception e) {
                // Exception treated as clientCiphers not set;
                // handled as default below
            }
        }
    }

    @Override
    public Socket makeSocket(String host, int port)
            throws IOException, UnknownHostException {
        return makeSocket(host, port, null, null, 0);
    }

    @Override
    public Socket makeSocket(String host, int port,
            SSLCertificateApprovalCallback certApprovalCallback,
            SSLClientCertificateSelectionCallback clientCertCallback,
            int timeout // milliseconds
    ) throws IOException, UnknownHostException {

        try {
            s = new SSLSocket(host, port, null, 0, certApprovalCallback,
                    clientCertCallback);

            Socket js = new Socket(InetAddress.getByName(host), port);
            s = new SSLSocket(js, host,
                    certApprovalCallback,
                    clientCertCallback);

            s.setUseClientMode(true);
            s.setSoTimeout(timeout);

            SSLHandshakeCompletedListener listener = null;

            listener = new ClientHandshakeCB(this);
            s.addHandshakeCompletedListener(listener);
            if (this.sockListener != null)
                s.addSocketListener(this.sockListener);

           /** opt for general setting in JssSSLSocketFactory() constructor
            *  above rather than socket-specific setting
            *
            if (mClientCiphers != null && !mClientCiphers.isEmpty())
            CryptoUtil.setClientCiphers(s, mClientCiphers);
            */

            if (mClientAuthCertNickname != null) {
                // 052799 setClientCertNickname does not
                // report error if the nickName is invalid.
                // So we check this ourself using
                // findCertByNickname
                CryptoManager.getInstance().findCertByNickname(mClientAuthCertNickname);

                s.setClientCertNickname(mClientAuthCertNickname);
            }
            s.forceHandshake();

        } catch (org.mozilla.jss.crypto.ObjectNotFoundException e) {
            throw new IOException(e.toString(), e);

        } catch (org.mozilla.jss.crypto.TokenException e) {
            throw new IOException(e.toString(), e);

        } catch (UnknownHostException e) {
            throw e;

        } catch (IOException e) {
            throw e;

        } catch (Exception e) {
            throw new IOException(e.toString(), e);
        }

        return s;
    }

    @Override
    public Socket makeSocket(String host, int port,
            int timeout // milliseconds
    ) throws IOException, UnknownHostException {
        Thread t = new ConnectAsync(this, host, port);

        t.start();
        try {
            t.join(timeout);
        } catch (InterruptedException e) {
        }

        if (t.isAlive()) {
        }

        return s;
    }

    public void addSocketListener(SSLSocketListener sl) {
        this.sockListener = sl;
    }

    static class ClientHandshakeCB implements SSLHandshakeCompletedListener {
        Object sc;

        public ClientHandshakeCB(Object sc) {
            this.sc = sc;
        }

        @Override
        public void handshakeCompleted(SSLHandshakeCompletedEvent event) {
        }
    }
}
