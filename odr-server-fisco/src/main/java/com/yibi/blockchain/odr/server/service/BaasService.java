package com.yibi.blockchain.odr.server.service;

import com.yibi.blockchain.odr.fisco.entity.EvidenceData;
import com.yibi.blockchain.odr.fisco.service.EvidenceFace;
import org.bcos.web3j.abi.datatypes.Address;
import org.bcos.web3j.abi.datatypes.Utf8String;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

import static com.yibi.blockchain.odr.fisco.Constants.*;
import static com.yibi.blockchain.odr.fisco.utils.LogUtils.getErrorLogger;
import static com.yibi.blockchain.odr.fisco.utils.LogUtils.getMonitorLogger;

@Service
public class BaasService {

    private Logger logger = LoggerFactory.getLogger(BaasService.class);

    @Autowired
    private EvidenceFace evidenceSDK;

    public String sendToChain(String evidenceID, String evidenceHash) {
        try {
            // 证据上链
            long stNewEvidence = System.currentTimeMillis();
            Address address = evidenceSDK.newEvidence(
                    new Utf8String(evidenceHash),
                    new Utf8String(evidenceID),
                    new Utf8String(evidenceID),
                    evidenceSDK.signMessage(evidenceHash));
            long etNewEvidence = System.currentTimeMillis();
            getMonitorLogger().info(CODE_MONI_10001, etNewEvidence - stNewEvidence, MSG_MONI_10001);
            logger.info("new evidence address: {}", address);
            if (address != null)
                return address.toString();
        } catch (Exception e) {
            getErrorLogger().error("数据:{} 上链异常", evidenceID, e);
            getErrorLogger().error(CODE_ERR_S2001, MSG_ERR_S2001);
        }
        return null;
    }

    public EvidenceData getEvidenceData(String evidenceAddress) {
        EvidenceData evidenceData = null;
        try {
            evidenceData = evidenceSDK.getMessagebyHash(evidenceAddress);
        } catch (InterruptedException | ExecutionException e) {
            getErrorLogger().error(String.format("获取证据异常，evidenceAddress： %s", evidenceAddress), e);
        }
        return evidenceData;
    }

}
