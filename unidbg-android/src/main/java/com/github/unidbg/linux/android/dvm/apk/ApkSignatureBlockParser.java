package com.github.unidbg.linux.android.dvm.apk;

import com.android.apksig.ApkVerifier;
import net.dongliu.apk.parser.bean.CertificateMeta;
import net.dongliu.apk.parser.parser.CertificateMetas;

import java.io.File;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

final class ApkSignatureBlockParser {

    private ApkSignatureBlockParser() {
    }

    static CertificateMeta[] parse(File apkFile) {
        List<X509Certificate> signerCertificates = loadSignerCertificates(apkFile);
        if (signerCertificates.isEmpty()) {
            return new CertificateMeta[0];
        }

        try {
            return CertificateMetas.from(signerCertificates).toArray(new CertificateMeta[0]);
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<X509Certificate> loadSignerCertificates(File apkFile) {
        try {
            ApkVerifier.Result result = new ApkVerifier.Builder(apkFile).build().verify();
            boolean verified = result.isVerified();
            if (!verified) {
                return Collections.emptyList();
            }

            List<X509Certificate> signerCertificates = result.getSignerCertificates();
            return signerCertificates != null ? signerCertificates : Collections.<X509Certificate>emptyList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse APK signing block: " + apkFile, e);
        }
    }
}
