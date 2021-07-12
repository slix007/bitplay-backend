package com.bitplay.api.controller.config;


import com.bitplay.persistance.domain.settings.ContractMode;
import com.bitplay.persistance.domain.settings.ContractType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;

@JsonComponent
public class ContractModeDeserializer extends JsonDeserializer<ContractMode> {

    @Override
    public ContractMode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        TreeNode treeNode = p.getCodec().readTree(p);

        ContractType left = null;
        ContractType right = null;
        if (treeNode.get("left") != null) {
            left = ContractMode.parseContractType(((TextNode) treeNode.get("left")).asText());
        }
        if (treeNode.get("right") != null) {
            right = ContractMode.parseContractType(((TextNode) treeNode.get("right")).asText());
        }
        return new ContractMode(left, right);
    }
}
