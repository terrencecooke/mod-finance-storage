package org.folio.dao.budget;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class BudgetPostgresDAO implements BudgetDAO {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  public Future<Integer> updateBatchBudgets(String sql, DBClient client) {
    Promise<Integer> promise = Promise.promise();

    client.getPgClient().execute(client.getConnection(), sql, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        promise.complete(reply.result().getUpdated());
      }
    });
    return promise.future();
  }

  public Future<List<Budget>> getBudgets(String sql, JsonArray params, DBClient client) {
    Promise<List<Budget>> promise = Promise.promise();
    client.getPgClient()
      .select(client.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Budget> budgets = reply.result()
            .getResults()
            .stream()
            .flatMap(JsonArray::stream)
            .map(o -> new JsonObject(o.toString()).mapTo(Budget.class))
            .collect(Collectors.toList());
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  public Future<List<Budget>> getBudgets(Criterion criterion, DBClient client) {
    Promise<List<Budget>> promise = Promise.promise();
    client.getPgClient().get(BUDGET_TABLE, Budget.class, criterion, false, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Budget> budgets = reply.result()
            .getResults();
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  public Future<Budget> getBudgetById(String id, DBClient client) {
    Promise<Budget> promise = Promise.promise();

    logger.debug("Get budget={}", id);

    client.getPgClient().getById(BUDGET_TABLE, id, reply -> {
      if (reply.failed()) {
        logger.error("Budget retrieval with id={} failed", reply.cause(), id);
        handleFailure(promise, reply);
      } else {
        final JsonObject budget = reply.result();
        if (budget == null) {
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          logger.debug("Budget with id={} successfully extracted", id);
          promise.complete(budget.mapTo(Budget.class));
        }
      }
    });
    return promise.future();
  }

  public Future<DBClient> deleteBudget(String id, DBClient client) {
    Promise<DBClient> promise = Promise.promise();
    client.getPgClient().delete(client.getConnection(), BUDGET_TABLE, id, reply -> {
      if (reply.result().getUpdated() == 0) {
        promise.fail(new HttpStatusException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase()));
      } else {
        promise.complete(client);
      }
    });
    return promise.future();
  }

}
