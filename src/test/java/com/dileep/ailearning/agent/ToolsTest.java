package com.dileep.ailearning.agent;

import com.dileep.ailearning.agent.tools.ErpDataStore;
import com.dileep.ailearning.agent.tools.GetCustomerTool;
import com.dileep.ailearning.agent.tools.QueryLedgerTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Module 5: the tools themselves — execution and their advertised definitions. */
class ToolsTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ErpDataStore erp = new ErpDataStore();
    private final GetCustomerTool getCustomer = new GetCustomerTool(erp, objectMapper);
    private final QueryLedgerTool queryLedger = new QueryLedgerTool(erp, objectMapper);

    @Test
    void getCustomerReturnsJsonForKnownId() {
        String result = getCustomer.execute(Map.of("id", "C-100"));
        assertThat(result).contains("Bharat Motors Ltd").contains("GOLD").contains("142500.00");
    }

    @Test
    void getCustomerThrowsHelpfulErrorForUnknownId() {
        assertThatThrownBy(() -> getCustomer.execute(Map.of("id", "C-999")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("No customer with id 'C-999'")
                .hasMessageContaining("C-100"); // suggests valid ids
    }

    @Test
    void queryLedgerComputesNetBalance() {
        // 4000-SALES: credits 30149 + 11800 = 41949, debits 2360 → net 39589.00
        String result = queryLedger.execute(Map.of("account", "4000-SALES"));
        assertThat(result).contains("\"netBalance\":39589.00");
    }

    @Test
    void queryLedgerThrowsForUnknownAccount() {
        assertThatThrownBy(() -> queryLedger.execute(Map.of("account", "9999-NOPE")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("No ledger for account '9999-NOPE'");
    }

    @Test
    void toolDefinitionsAreWellFormed() {
        var def = getCustomer.definition();
        assertThat(def.type()).isEqualTo("function");
        assertThat(def.function().name()).isEqualTo("get_customer");
        assertThat(def.function().description()).isNotBlank();
        assertThat(def.function().parameters()).containsKey("properties");
        assertThat(getCustomer.requiredParameters()).containsExactly("id");
    }
}
