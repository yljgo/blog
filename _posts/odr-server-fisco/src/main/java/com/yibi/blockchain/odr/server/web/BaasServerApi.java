package com.yibi.blockchain.odr.server.web;

import com.yibi.blockchain.odr.fisco.entity.EvidenceData;
import com.yibi.blockchain.odr.server.service.BaasService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class BaasServerApi {

    @Autowired
    private BaasService baasService;

    @RequestMapping(value = "onChain", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> onChain(@RequestBody EvidenceData evidenceData) {
        String address = baasService.sendToChain(evidenceData.getEvidenceID(),
                evidenceData.getEvidenceHash());
        return ResponseEntity.ok(address);
    }

    @RequestMapping(value = "getHash", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> getHash(@RequestBody Map<String, String> params) {
        EvidenceData evidenceData = baasService.getEvidenceData(params.get("address"));
        if(evidenceData == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok(evidenceData.getEvidenceHash());
    }

}
