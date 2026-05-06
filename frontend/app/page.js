'use client';

import { useState, useEffect, useCallback } from 'react';

const API_BASE = typeof window !== 'undefined' ? '' : 'http://localhost:8080';

const MOCK_ACCOUNTS = [
  {
    accountId: 'account-joao-001',
    holderName: 'João Silva',
    holderDocument: '123.456.789-00',
    currencies: ['BRL', 'USD', 'EUR'],
  },
  {
    accountId: 'account-maria-002',
    holderName: 'Maria Santos',
    holderDocument: '987.654.321-00',
    currencies: ['BRL', 'EUR', 'GBP'],
  },
];

const CURRENCY_SYMBOLS = {
  BRL: 'R$', USD: '$', EUR: '€', GBP: '£', JPY: '¥',
};

function formatCurrency(amount, currency) {
  const symbol = CURRENCY_SYMBOLS[currency] || currency;
  return `${symbol} ${Number(amount).toLocaleString('pt-BR', { minimumFractionDigits: 2 })}`;
}

function formatDate(dateStr) {
  return new Date(dateStr).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}

export default function Home() {
  const [currentAccount, setCurrentAccount] = useState(null);
  const [activeTab, setActiveTab] = useState('extrato');
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [statement, setStatement] = useState(null);
  const [sagaStatus, setSagaStatus] = useState(null);
  const [activeSagaId, setActiveSagaId] = useState(null);
  const [currencyFilter, setCurrencyFilter] = useState(null);
  const [loading, setLoading] = useState(false);
  const [balances, setBalances] = useState([]);

  const [transferForm, setTransferForm] = useState({
    targetAccountId: '',
    transactionType: 'TRANSFER_OUT',
    amount: '',
    currency: 'BRL',
    targetCurrency: '',
    description: '',
  });

  const fetchStatement = useCallback(async () => {
    if (!currentAccount) return;
    setLoading(true);
    try {
      const params = currencyFilter ? `?currency=${currencyFilter}` : '';
      const res = await fetch(`/api/statements/${currentAccount.accountId}${params}`);
      if (res.ok) {
        const data = await res.json();
        setStatement(data);
        if (data.currentBalances) setBalances(data.currentBalances);
      }
    } catch (err) {
      console.error('Error fetching statement:', err);
    }
    setLoading(false);
  }, [currentAccount, currencyFilter]);

  const fetchAccountBalances = useCallback(async () => {
    if (!currentAccount) return;
    try {
      const res = await fetch(`/api/accounts/${currentAccount.accountId}`);
      if (res.ok) {
        const data = await res.json();
        if (data.balances) setBalances(data.balances);
      }
    } catch (err) {
      console.error('Error fetching balances:', err);
    }
  }, [currentAccount]);

  useEffect(() => {
    if (currentAccount) {
      fetchStatement();
      fetchAccountBalances();
    }
  }, [currentAccount, currencyFilter, fetchStatement, fetchAccountBalances]);

  useEffect(() => {
    if (!activeSagaId) return;
    const interval = setInterval(async () => {
      try {
        const res = await fetch(`/api/saga/status/${activeSagaId}`);
        if (res.ok) {
          const data = await res.json();
          setSagaStatus(data);
          if (data.currentStatus === 'COMPLETED' || data.currentStatus === 'FAILED') {
            clearInterval(interval);
            setTimeout(() => {
              fetchStatement();
              fetchAccountBalances();
            }, 1000);
          }
        }
      } catch (err) {
        console.error('Error polling saga:', err);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [activeSagaId, fetchStatement, fetchAccountBalances]);

  const handleTransfer = async () => {
    setLoading(true);
    try {
      const body = {
        sourceAccountId: currentAccount.accountId,
        targetAccountId: transferForm.targetAccountId || null,
        transactionType: transferForm.transactionType,
        amount: parseFloat(transferForm.amount),
        currency: transferForm.currency,
        targetCurrency: transferForm.targetCurrency || null,
        description: transferForm.description || `${transferForm.transactionType} via app`,
      };

      const res = await fetch('/api/saga/transaction', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });

      if (res.ok) {
        const data = await res.json();
        setActiveSagaId(data.sagaId);
        setSagaStatus({ currentStatus: 'STARTED', steps: [] });
        setShowTransferModal(false);
        setActiveTab('saga');
        setTransferForm({
          targetAccountId: '',
          transactionType: 'TRANSFER_OUT',
          amount: '',
          currency: 'BRL',
          targetCurrency: '',
          description: '',
        });
      }
    } catch (err) {
      console.error('Error submitting transaction:', err);
    }
    setLoading(false);
  };

  if (!currentAccount) {
    return (
      <div className="login-screen">
        <h1>Extrato Bancário</h1>
        <p>Multiconta & Multimoeda<br />Selecione uma conta para acessar</p>
        {MOCK_ACCOUNTS.map(acc => (
          <div key={acc.accountId} className="account-card" onClick={() => setCurrentAccount(acc)}>
            <div className="name">{acc.holderName}</div>
            <div className="doc">{acc.holderDocument}</div>
            <div className="currencies">
              {acc.currencies.map(c => (
                <span key={c} className="currency-tag">{c}</span>
              ))}
            </div>
          </div>
        ))}
      </div>
    );
  }

  const targetAccount = MOCK_ACCOUNTS.find(a => a.accountId !== currentAccount.accountId);

  return (
    <div>
      <header className="app-header">
        <h1>Extrato</h1>
        <div className="user-badge" onClick={() => setCurrentAccount(null)}>
          {currentAccount.holderName.split(' ')[0]}
        </div>
      </header>

      <div className="balance-card">
        <h2>Saldos - {currentAccount.holderName}</h2>
        <div className="balance-list">
          {balances.length > 0 ? balances.map((b, i) => (
            <div key={i} className="balance-item">
              <div className="currency">{b.currency}</div>
              <div className="amount">{formatCurrency(b.balance, b.currency)}</div>
            </div>
          )) : currentAccount.currencies.map(c => (
            <div key={c} className="balance-item">
              <div className="currency">{c}</div>
              <div className="amount">--</div>
            </div>
          ))}
        </div>
      </div>

      <div className="tabs">
        <button className={`tab ${activeTab === 'extrato' ? 'active' : ''}`} onClick={() => setActiveTab('extrato')}>
          Extrato
        </button>
        <button className={`tab ${activeTab === 'saga' ? 'active' : ''}`} onClick={() => setActiveTab('saga')}>
          Saga Status
        </button>
      </div>

      {activeTab === 'extrato' && (
        <div className="section">
          <div className="section-title">Extrato de Movimentações</div>

          <div className="currency-filter">
            <button
              className={`currency-chip ${!currencyFilter ? 'active' : ''}`}
              onClick={() => setCurrencyFilter(null)}
            >
              Todas
            </button>
            {currentAccount.currencies.map(c => (
              <button
                key={c}
                className={`currency-chip ${currencyFilter === c ? 'active' : ''}`}
                onClick={() => setCurrencyFilter(c)}
              >
                {c}
              </button>
            ))}
          </div>

          {loading && <div className="loading">Carregando...</div>}

          <div className="transaction-list">
            {statement?.entries?.length > 0 ? statement.entries.map((entry, i) => (
              <div key={i} className="tx-item">
                <div className="tx-info">
                  <div className="tx-type">{entry.transactionType} • {entry.currency}</div>
                  <div className="tx-desc">{entry.description}</div>
                  <div className="tx-date">{formatDate(entry.timestamp)}</div>
                </div>
                <div className={`tx-amount ${entry.direction === 'CREDIT' ? 'credit' : 'debit'}`}>
                  {entry.direction === 'CREDIT' ? '+' : '-'}{formatCurrency(entry.amount, entry.currency)}
                </div>
              </div>
            )) : (
              <div className="empty-state">
                Nenhuma movimentação encontrada.<br />
                Simule uma transferência para ver o extrato.
              </div>
            )}
          </div>

          <div style={{ marginTop: 16 }}>
            <button className="btn" onClick={() => setShowTransferModal(true)}>
              Simular Transferência
            </button>
          </div>
        </div>
      )}

      {activeTab === 'saga' && (
        <div className="section">
          <div className="section-title">
            <span className={`status-dot ${
              sagaStatus?.currentStatus === 'COMPLETED' ? 'green' :
              sagaStatus?.currentStatus === 'FAILED' ? 'red' : 'yellow'
            }`} />
            Fluxo da Saga
          </div>

          {sagaStatus ? (
            <>
              <div style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 8 }}>
                Saga ID: {activeSagaId?.slice(0, 8)}...
                <br />Status: <strong>{sagaStatus.currentStatus}</strong>
              </div>
              <div className="saga-steps">
                {sagaStatus.steps?.map((step, i) => (
                  <div key={i} className={`saga-step ${
                    step.status === 'COMPLETED' || step.status === 'ACCOUNT_VALIDATED' ||
                    step.status === 'CURRENCY_CONVERTED' || step.status === 'LEDGER_RECORDED'
                      ? 'completed'
                      : step.status?.includes('FAILED') ? 'failed' : 'in-progress'
                  }`}>
                    <div>
                      <div className="step-name">{step.stepName}</div>
                      <div className="step-service">{step.service}</div>
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      {step.timestamp ? formatDate(step.timestamp) : ''}
                    </div>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <div className="empty-state">
              Nenhuma saga ativa.<br />
              Simule uma transferência para ver o fluxo.
            </div>
          )}
        </div>
      )}

      {showTransferModal && (
        <div className="modal-overlay" onClick={() => setShowTransferModal(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Simular Transferência</h2>
              <button className="modal-close" onClick={() => setShowTransferModal(false)}>&times;</button>
            </div>

            <div className="form-group">
              <label>Tipo de Transação</label>
              <select value={transferForm.transactionType} onChange={e => setTransferForm({...transferForm, transactionType: e.target.value})}>
                <option value="TRANSFER_OUT">Transferência (Enviar)</option>
                <option value="DEPOSIT">Depósito</option>
                <option value="WITHDRAWAL">Saque</option>
                <option value="CURRENCY_EXCHANGE">Câmbio</option>
              </select>
            </div>

            {(transferForm.transactionType === 'TRANSFER_OUT') && (
              <div className="form-group">
                <label>Conta Destino</label>
                <select value={transferForm.targetAccountId} onChange={e => setTransferForm({...transferForm, targetAccountId: e.target.value})}>
                  <option value="">Selecione...</option>
                  <option value={targetAccount.accountId}>
                    {targetAccount.holderName} ({targetAccount.accountId.slice(-3)})
                  </option>
                </select>
              </div>
            )}

            <div className="form-row">
              <div className="form-group">
                <label>Valor</label>
                <input
                  type="number"
                  placeholder="0.00"
                  value={transferForm.amount}
                  onChange={e => setTransferForm({...transferForm, amount: e.target.value})}
                />
              </div>
              <div className="form-group">
                <label>Moeda</label>
                <select value={transferForm.currency} onChange={e => setTransferForm({...transferForm, currency: e.target.value})}>
                  {currentAccount.currencies.map(c => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>
            </div>

            {transferForm.transactionType === 'CURRENCY_EXCHANGE' && (
              <div className="form-group">
                <label>Moeda Destino</label>
                <select value={transferForm.targetCurrency} onChange={e => setTransferForm({...transferForm, targetCurrency: e.target.value})}>
                  <option value="">Selecione...</option>
                  {currentAccount.currencies.filter(c => c !== transferForm.currency).map(c => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>
            )}

            <div className="form-group">
              <label>Descrição (opcional)</label>
              <input
                type="text"
                placeholder="Descrição da transação"
                value={transferForm.description}
                onChange={e => setTransferForm({...transferForm, description: e.target.value})}
              />
            </div>

            <button
              className="btn"
              onClick={handleTransfer}
              disabled={!transferForm.amount || loading}
            >
              {loading ? 'Processando...' : 'Publicar Evento no Kafka'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
