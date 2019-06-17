package com.yibi.blockchain.odr.test;

import com.yibi.blockchain.odr.fisco.entity.EvidenceData;
import com.yibi.blockchain.odr.server.BaasApplication;
import com.yibi.blockchain.odr.server.service.BaasService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = BaasApplication.class)
public class BaasServiceTest {

    @Autowired
    private BaasService baasService;

    @Test
    public void test() {
        String address = baasService.sendToChain("id01", "hash001");
        System.out.println(address);
        if(address == null) return;
        EvidenceData data =  baasService.getEvidenceData(address);
        System.out.println(data.toString());
    }

}
