package com.dileep.ailearning.agent.tools;

import com.dileep.ailearning.agent.Tool;
import com.dileep.ailearning.agent.ToolExecutionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tool: look up a customer by id (Module 5).
 */
@Component
public class GetCustomerTool implements Tool {

    private final ErpDataStore erp;
    private final ObjectMapper objectMapper;

    public GetCustomerTool(ErpDataStore erp, ObjectMapper objectMapper) {
        this.erp = erp;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "get_customer";
    }

    @Override
    public String description() {
        return "Look up a customer by their id (e.g. \"C-100\"). Returns the customer's "
                + "name, tier, credit limit and outstanding balance.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "id", Map.of(
                                "type", "string",
                                "description", "The customer id, e.g. C-100")),
                "required", List.of("id"));
    }

    @Override
    public List<String> requiredParameters() {
        return List.of("id");
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String id = String.valueOf(arguments.get("id")).trim();
        var customer = erp.findCustomer(id)
                .orElseThrow(() -> new ToolExecutionException(
                        "No customer with id '" + id + "'. Known customer ids: " + erp.customerIds()));
        try {
            return objectMapper.writeValueAsString(customer);
        } catch (JsonProcessingException e) {
            throw new ToolExecutionException("Failed to serialize customer: " + e.getMessage());
        }
    }
}
