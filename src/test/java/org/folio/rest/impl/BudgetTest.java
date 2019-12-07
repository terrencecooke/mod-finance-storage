package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Test;

class BudgetTest extends TestBase {

  private static final String BUDGET_ENDPOINT = TestEntities.BUDGET.getEndpoint();
  private static final String BUDGET_TEST_TENANT = "budget_test_tenant";
  private static final Header BUDGET_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, BUDGET_TEST_TENANT);

  @Test
  void testGetQuery() throws MalformedURLException {
    prepareTenant(BUDGET_TENANT_HEADER, true, true);

    // search for GET
    verifyCollectionQuantity(BUDGET_ENDPOINT, 21, BUDGET_TENANT_HEADER);

    // search with fields from "fund"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fund.fundStatus==Inactive", 2, BUDGET_TENANT_HEADER);
    // search with fields from "FY"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fiscalYear.name==FY18", 3, BUDGET_TENANT_HEADER);
    // search with fields from "ledgers"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=ledger.name==Ongoing", 7, BUDGET_TENANT_HEADER);
    // complex query
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fund.fundStatus==Active AND ledger.name==Ongoing AND fiscalYear.code==FY2019", 4, BUDGET_TENANT_HEADER);

    // search with invalid cql query
    testInvalidCQLQuery(BUDGET_ENDPOINT + "?query=invalid-query");
    deleteTenant(BUDGET_TENANT_HEADER);
  }

  @Test
  void testPostBudgetAllocatedValue() throws MalformedURLException {
    FiscalYear fiscalYearOne = new JsonObject(getFile(FISCAL_YEAR.getSampleFileName())).mapTo(FiscalYear.class);
    String fiscalYearId = createEntity(FISCAL_YEAR.getEndpoint(), fiscalYearOne.withCode("FY2017"));

    Ledger ledger = new JsonObject(getFile(LEDGER.getPathToSampleFile())).mapTo(Ledger.class).withId(null);
    String ledgerId = createEntity(LEDGER.getEndpoint(), ledger.withCode("first").withName(ledger.getCode()).withFiscalYearOneId(fiscalYearId));

    Fund fund = new JsonObject(getFile(FUND.getPathToSampleFile())).mapTo(Fund.class).withLedgerId(ledgerId).withId(null).withFundTypeId(null);
    String fundId = createEntity(FUND.getEndpoint(), fund.withCode("first").withName(fund.getCode()).withFundStatus(Fund.FundStatus.ACTIVE));

    Budget budget = new JsonObject(getFile(BUDGET.getPathToSampleFile())).mapTo(Budget.class).withId(null);
    String budgetId = createEntity(BUDGET.getEndpoint(), budget.withBudgetStatus(Budget.BudgetStatus.ACTIVE).withName("current")
      .withFundId(fundId).withFiscalYearId(fiscalYearId).withAllocated(100.0));

    Budget responseBudget = getDataById(BUDGET.getEndpointWithId(), budgetId).getBody()
      .as(Budget.class);
    assertEquals(budget.getAllocated(),responseBudget.getAllocated());
    verifyAndDeleteBudgetAllocationTransactions();

    deleteDataSuccess(BUDGET.getEndpointWithId(), budgetId);
    deleteDataSuccess(FUND.getEndpointWithId(), fundId);
    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
    deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), fiscalYearId);

  }
}
