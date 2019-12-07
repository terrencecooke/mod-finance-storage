package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.Source;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;
import org.folio.rest.jaxrs.resource.FinanceStorageBudgets;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;

public class BudgetAPI implements FinanceStorageBudgets {
  public static final String BUDGET_TABLE = "budget";
  public static final String FISCAL_YEAR_TABLE = "fiscal_year";
  public static final String TRANSACTION_TABLE = "transaction";

  private static final Logger log = LoggerFactory.getLogger(BudgetAPI.class);
  private static final String CURRENCY_NOT_FOUND_FOR_FISCAL_YEAR = "Currency not found for Fiscal Year";

  private PostgresClient pgClient;

  public BudgetAPI(Vertx vertx, String tenantId) {
    pgClient = PostgresClient.getInstance(vertx, tenantId);
  }


  @Override
  @Validate
  public void getFinanceStorageBudgets(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(BUDGET_TABLE, Budget.class, BudgetCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageBudgetsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageBudgets(String lang, Budget entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Tx<Budget> tx = new Tx<>(entity, pgClient);
    vertxContext.runOnContext(event ->
      HelperUtils.startTx(tx)
        .compose(this::saveBudget)
        .compose(this::saveAllocationTransaction)
        .compose(HelperUtils::endTx)
        .setHandler(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("Creation of budget record {} has failed", cause, tx.getEntity());

            // The result of rollback operation is not so important, main failure cause is used to build the response
            HelperUtils.rollbackTransaction(tx)
              .setHandler(res -> HelperUtils.replyWithErrorResponse(asyncResultHandler, cause));
          } else {
            log.info("Budget record {} and associated data were successfully created", tx.getEntity());
            asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageBudgetsResponse
                .respond201WithApplicationJson(result.result().getEntity(), PostFinanceStorageBudgetsResponse.headersFor201()
                  .withLocation(HelperUtils.getEndpoint(FinanceStorageBudgets.class) + result.result().getEntity().getId()))));
          }
        })
    );
  }

  @Override
  @Validate
  public void getFinanceStorageBudgetsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(BUDGET_TABLE, Budget.class, id, okapiHeaders, vertxContext, GetFinanceStorageBudgetsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageBudgetsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(BUDGET_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageBudgetsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageBudgetsById(String id, String lang, Budget entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(BUDGET_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageBudgetsByIdResponse.class, asyncResultHandler);
  }


  private Future<Tx<Budget>> saveAllocationTransaction(Tx<Budget> tx) {
    //Create a transaction only if allocated amount is specified
    if (tx.getEntity().getAllocated() > 0) {
      return getCurrency(tx).map(currency -> buildTransaction(currency, tx.getEntity()))
        .compose(transaction -> saveTransaction(transaction, tx));
    }
    return Future.succeededFuture(tx);
  }

  private Future<String> getCurrency(Tx<Budget> tx) {
    Promise<String> promise = Promise.promise();
    tx.getPgClient()
      .getById(FISCAL_YEAR_TABLE, tx.getEntity()
        .getFiscalYearId(), FiscalYear.class, event -> {
          if (event.failed()) {
            HelperUtils.handleFailure(promise, event);
          } else {
            if (event.result() != null) {
              promise.complete(event.result()
                .getCurrency());
            } else {
              log.error(CURRENCY_NOT_FOUND_FOR_FISCAL_YEAR);
              promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), CURRENCY_NOT_FOUND_FOR_FISCAL_YEAR));
            }
          }

        });
    return promise.future();
  }

  private Transaction buildTransaction(String currency, Budget budget) {
    return new Transaction().withCurrency(currency)
      .withAmount(budget.getAllocated())
      .withFiscalYearId(budget.getFiscalYearId())
      .withToFundId(budget.getFundId())
      .withTransactionType(TransactionType.ALLOCATION)
      .withSource(Source.USER);
  }

  private Future<Tx<Budget>> saveTransaction(Transaction transaction, Tx<Budget> tx) {
    Promise<Tx<Budget>> promise = Promise.promise();
    pgClient.save(tx.getConnection(), TRANSACTION_TABLE, transaction, event -> {
      if (event.failed()) {
        HelperUtils.handleFailure(promise, event);
      } else {
        log.info("Allocation Transaction for budget record {} was successfully created", tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private Future<Tx<Budget>> saveBudget(Tx<Budget> tx) {
    Promise<Tx<Budget>> promise = Promise.promise();

    Budget budget = tx.getEntity();
    if (budget.getId() == null) {
      budget.setId(UUID.randomUUID()
        .toString());
    }

    pgClient.save(tx.getConnection(), BUDGET_TABLE, budget.getId(), budget, event -> {
      if (event.failed()) {
        HelperUtils.handleFailure(promise, event);
      } else {
        log.info("budget record {} was successfully created", tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

}
