package org.folio.service.summary;

import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;

public class EncumbranceTransactionSummaryService extends AbstractTransactionSummaryService<OrderTransactionSummary> {

  public EncumbranceTransactionSummaryService(TransactionSummaryDao<OrderTransactionSummary> transactionSummaryDao) {
    super(transactionSummaryDao);
  }

  @Override
  protected String getSummaryId(Transaction transaction) {
    return transaction.getEncumbrance().getSourcePurchaseOrderId();
  }

  @Override
  protected boolean isProcessed(OrderTransactionSummary summary) {
    return summary.getNumTransactions() < 0;
  }

  @Override
  protected void setTransactionsSummariesProcessed(OrderTransactionSummary summary) {
    summary.setNumTransactions(-summary.getNumTransactions());
  }

  @Override
  public Integer getNumTransactions(OrderTransactionSummary summary) {
    return summary.getNumTransactions();
  }
}
