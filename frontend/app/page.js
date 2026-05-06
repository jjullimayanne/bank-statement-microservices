'use client';

import { useState, useEffect, useCallback } from 'react';

const MOCK_ACCOUNTS = [
  {
    accountId: 'account-joao-001',
    holderName: 'João Silva',
    holderDocument: '123.456.789-00',
    currencies: ['BRL', 'USD', 'EUR'],
    agency: '0001',
    accountNumber: '12345-6',
  },
  {
    accountId: 'account-maria-002',
    holderName: 'Maria Santos',
    holderDocument: '987.654.321-00',
    currencies: ['BRL', 'EUR', 'GBP'],
    agency: '0001',
    accountNumber: '78901-2',
  },
];

const CURRENCY_CONFIG = {
  BRL: { symbol: 'R$', flag: '🇧🇷', name: 'Real' },
  USD: { symbol: '$', flag: '🇺🇸', name: 'Dólar' },
  EUR: { symbol: '€', flag: '🇪🇺', name: 'Euro' },
  GBP: { symbol: '£', flag: '🇬🇧', name: 'Libra' },
  JPY: { symbol: '¥', flag: '🇯🇵', name: 'Iene' },
};

function formatCurrency(amount, currency) {
  const config = CURRENCY_CONFIG[currency] || { symbol: currency };
  return `${config.symbol} ${Number(amount).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatDate(dateStr) {
  const d = new Date(dateStr);
  return d.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function formatDateGroup(dateStr) {
  const d = new Date(dateStr);
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);

  if (d.toDateString() === today.toDateString()) return 'Hoje';
  if (d.toDateString() === yesterday.toDateString()) return 'Ontem';
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'long' });
}

function getGreeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Bom dia';
  if (h < 18) return 'Boa tarde';
  return 'Boa noite';
}

function getInitials(name) {
  return name.split(' ').map(n => n[0]).join('').slice(0, 2);
}

function getTxIcon(direction) {
  if (direction === 'CREDIT') return '↓';
  return '↑';
}

function getTxLabel(entry) {
  if (entry.transactionType === 'CREDIT') return 'Transferência recebida';
  if (entry.transactionType === 'DEBIT') return 'Transferência enviada';
  if (entry.direction === 'CREDIT') return 'Crédito';
  return 'Débito';
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
  const [showBalance, setShowBalance] = useState(true);

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

  // ─── Login Screen ───────────────────────────────
  if (!currentAccount) {
    return (
      <div className="login-screen">
        <div className="login-header">
          <div className="login-logo">B</div>
          <div className="login-title">BankStatement</div>
          <div className="login-subtitle">
            Extrato Bancário Multiconta e Multimoeda
          </div>
        </div>
        <div className="login-body">
          <h2>Selecione sua conta</h2>
          {MOCK_ACCOUNTS.map(acc => (
            <div key={acc.accountId} className="account-card" onClick={() => setCurrentAccount(acc)}>
              <div className="account-avatar">{getInitials(acc.holderName)}</div>
              <div className="account-info">
                <div className="name">{acc.holderName}</div>
                <div className="doc">Ag {acc.agency} • Cc {acc.accountNumber}</div>
                <div className="currencies">
                  {acc.currencies.map(c => (
                    <span key={c} className="currency-tag">
                      {CURRENCY_CONFIG[c]?.flag} {c}
                    </span>
                  ))}
                </div>
              </div>
              <span className="account-arrow">›</span>
            </div>
          ))}
        </div>
      </div>
    );
  }

  const targetAccount = MOCK_ACCOUNTS.find(a => a.accountId !== currentAccount.accountId);
  const mainBalance = balances.find(b => b.currency === 'BRL');
  const mainBalanceValue = mainBalance ? mainBalance.balance : 0;

  const groupedEntries = {};
  if (statement?.entries) {
    statement.entries.forEach(entry => {
      const group = formatDateGroup(entry.timestamp);
      if (!groupedEntries[group]) groupedEntries[group] = [];
      groupedEntries[group].push(entry);
    });
  }

  return (
    <div className="page-content">
      {/* Header */}
      <header className="app-header">
        <div className="header-top">
          <div className="header-logo">
            <div className="header-logo-icon">B</div>
            <span className="header-logo-text">BankStatement</span>
          </div>
          <div className="header-user" onClick={() => setCurrentAccount(null)}>
            <div className="header-user-avatar">{getInitials(currentAccount.holderName)}</div>
            <span className="header-user-name">{currentAccount.holderName.split(' ')[0]}</span>
          </div>
        </div>
        <div className="header-greeting">{getGreeting()}, {currentAccount.holderName.split(' ')[0]}</div>
        <div className="header-balance-label">Saldo disponível</div>
        <div className="header-balance-value" onClick={() => setShowBalance(!showBalance)}>
          {showBalance ? formatCurrency(mainBalanceValue, 'BRL') : '••••••'}
          <span className="header-balance-currency">BRL</span>
        </div>
      </header>

      {/* Balance Cards */}
      <div className="balance-cards">
        {(balances.length > 0 ? balances : currentAccount.currencies.map(c => ({ currency: c, balance: 0 }))).map((b, i) => (
          <div key={i} className="balance-card">
            <div className="currency-label">
              <span className="currency-flag">{CURRENCY_CONFIG[b.currency]?.flag || '💱'}</span>
              <span className="currency-code">{b.currency}</span>
            </div>
            <div className="balance-value">
              {showBalance ? formatCurrency(b.balance, b.currency) : '••••'}
            </div>
          </div>
        ))}
      </div>

      {/* Quick Actions */}
      <div className="quick-actions">
        <button className="quick-action" onClick={() => { setTransferForm({...transferForm, transactionType: 'TRANSFER_OUT'}); setShowTransferModal(true); }}>
          <div className="quick-action-icon transfer">↗</div>
          <span className="quick-action-label">Transferir</span>
        </button>
        <button className="quick-action" onClick={() => { setTransferForm({...transferForm, transactionType: 'DEPOSIT'}); setShowTransferModal(true); }}>
          <div className="quick-action-icon deposit">↓</div>
          <span className="quick-action-label">Depositar</span>
        </button>
        <button className="quick-action" onClick={() => { setTransferForm({...transferForm, transactionType: 'CURRENCY_EXCHANGE'}); setShowTransferModal(true); }}>
          <div className="quick-action-icon exchange">⇄</div>
          <span className="quick-action-label">Câmbio</span>
        </button>
        <button className="quick-action" onClick={() => { setTransferForm({...transferForm, transactionType: 'WITHDRAWAL'}); setShowTransferModal(true); }}>
          <div className="quick-action-icon pix">↑</div>
          <span className="quick-action-label">Sacar</span>
        </button>
      </div>

      {/* Tabs */}
      <div className="tabs">
        <button className={`tab ${activeTab === 'extrato' ? 'active' : ''}`} onClick={() => setActiveTab('extrato')}>
          Extrato
        </button>
        <button className={`tab ${activeTab === 'saga' ? 'active' : ''}`} onClick={() => setActiveTab('saga')}>
          Saga
        </button>
      </div>

      {/* Extrato Tab */}
      {activeTab === 'extrato' && (
        <div className="section">
          <div className="section-header">
            <div className="section-title">Movimentações</div>
            <button className="section-action" onClick={fetchStatement}>Atualizar</button>
          </div>

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
                {CURRENCY_CONFIG[c]?.flag} {c}
              </button>
            ))}
          </div>

          {loading && (
            <div className="loading">
              <div className="loading-spinner" />
              Carregando...
            </div>
          )}

          <div className="transaction-list">
            {Object.keys(groupedEntries).length > 0 ? (
              Object.entries(groupedEntries).map(([group, entries]) => (
                <div key={group}>
                  <div className="tx-date-group">{group}</div>
                  {entries.map((entry, i) => (
                    <div key={i} className="tx-item">
                      <div className={`tx-icon ${entry.direction === 'CREDIT' ? 'credit' : 'debit'}`}>
                        {getTxIcon(entry.direction)}
                      </div>
                      <div className="tx-info">
                        <div className="tx-desc">{getTxLabel(entry)}</div>
                        <div className="tx-meta">
                          <span>{entry.currency}</span>
                          <span className="dot" />
                          <span>{formatDate(entry.timestamp)}</span>
                        </div>
                      </div>
                      <div className="tx-amount">
                        <div className={`value ${entry.direction === 'CREDIT' ? 'credit' : 'debit'}`}>
                          {entry.direction === 'CREDIT' ? '+' : '-'}{formatCurrency(entry.amount, entry.currency)}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ))
            ) : !loading && (
              <div className="empty-state">
                <div className="empty-state-icon">📋</div>
                <div className="empty-state-text">
                  Nenhuma movimentação encontrada.<br />
                  Use os botões acima para simular uma transação.
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Saga Tab */}
      {activeTab === 'saga' && (
        <div className="section">
          <div className="section-header">
            <div className="section-title">Fluxo da Saga</div>
          </div>

          {sagaStatus ? (
            <>
              <div className="saga-info-bar">
                <div>
                  <div className="saga-info-label">Saga ID</div>
                  <div className="saga-info-value">{activeSagaId?.slice(0, 12)}...</div>
                </div>
                <span className={`saga-status-badge ${
                  sagaStatus.currentStatus === 'COMPLETED' ? 'completed' :
                  sagaStatus.currentStatus === 'FAILED' ? 'failed' : 'processing'
                }`}>
                  {sagaStatus.currentStatus === 'COMPLETED' ? 'Concluída' :
                   sagaStatus.currentStatus === 'FAILED' ? 'Falhou' : 'Processando'}
                </span>
              </div>

              <div className="saga-timeline">
                {sagaStatus.steps?.map((step, i) => {
                  const isCompleted = step.status === 'COMPLETED' ||
                    step.status === 'ACCOUNT_VALIDATED' ||
                    step.status === 'CURRENCY_CONVERTED' ||
                    step.status === 'LEDGER_RECORDED';
                  const isFailed = step.status?.includes('FAILED');

                  return (
                    <div key={i} className={`saga-step ${
                      isCompleted ? 'completed' : isFailed ? 'failed' : 'in-progress'
                    }`}>
                      <div className="saga-step-name">{step.stepName}</div>
                      <div className="saga-step-service">{step.service}</div>
                      {step.timestamp && <div className="saga-step-time">{formatDate(step.timestamp)}</div>}
                    </div>
                  );
                })}
              </div>
            </>
          ) : (
            <div className="empty-state">
              <div className="empty-state-icon">⚡</div>
              <div className="empty-state-text">
                Nenhuma saga ativa.<br />
                Inicie uma transferência para acompanhar o fluxo em tempo real.
              </div>
            </div>
          )}
        </div>
      )}

      {/* Bottom Navigation */}
      <nav className="bottom-nav">
        <button className={`nav-item ${activeTab === 'extrato' ? 'active' : ''}`} onClick={() => setActiveTab('extrato')}>
          <span className="nav-item-icon">📊</span>
          <span className="nav-item-label">Extrato</span>
        </button>
        <button className="nav-item" onClick={() => setShowTransferModal(true)}>
          <span className="nav-item-icon">💸</span>
          <span className="nav-item-label">Transferir</span>
        </button>
        <button className={`nav-item ${activeTab === 'saga' ? 'active' : ''}`} onClick={() => setActiveTab('saga')}>
          <span className="nav-item-icon">⚡</span>
          <span className="nav-item-label">Saga</span>
        </button>
      </nav>

      {/* Transfer Modal */}
      {showTransferModal && (
        <div className="modal-overlay" onClick={() => setShowTransferModal(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-handle" />
            <div className="modal-header">
              <h2>
                {transferForm.transactionType === 'TRANSFER_OUT' ? 'Transferir' :
                 transferForm.transactionType === 'DEPOSIT' ? 'Depositar' :
                 transferForm.transactionType === 'CURRENCY_EXCHANGE' ? 'Câmbio' : 'Sacar'}
              </h2>
              <button className="modal-close" onClick={() => setShowTransferModal(false)}>✕</button>
            </div>

            <div className="form-group">
              <label>Tipo de operação</label>
              <select value={transferForm.transactionType} onChange={e => setTransferForm({...transferForm, transactionType: e.target.value})}>
                <option value="TRANSFER_OUT">Transferência</option>
                <option value="DEPOSIT">Depósito</option>
                <option value="WITHDRAWAL">Saque</option>
                <option value="CURRENCY_EXCHANGE">Câmbio</option>
              </select>
            </div>

            {transferForm.transactionType === 'TRANSFER_OUT' && (
              <div className="form-group">
                <label>Conta destino</label>
                <select value={transferForm.targetAccountId} onChange={e => setTransferForm({...transferForm, targetAccountId: e.target.value})}>
                  <option value="">Selecione o favorecido...</option>
                  <option value={targetAccount.accountId}>
                    {targetAccount.holderName} — Ag {targetAccount.agency} Cc {targetAccount.accountNumber}
                  </option>
                </select>
              </div>
            )}

            <div className="form-row">
              <div className="form-group">
                <label>Valor</label>
                <input
                  type="number"
                  step="0.01"
                  placeholder="0,00"
                  value={transferForm.amount}
                  onChange={e => setTransferForm({...transferForm, amount: e.target.value})}
                />
              </div>
              <div className="form-group" style={{maxWidth: 110}}>
                <label>Moeda</label>
                <select value={transferForm.currency} onChange={e => setTransferForm({...transferForm, currency: e.target.value})}>
                  {currentAccount.currencies.map(c => (
                    <option key={c} value={c}>{CURRENCY_CONFIG[c]?.flag} {c}</option>
                  ))}
                </select>
              </div>
            </div>

            {transferForm.transactionType === 'CURRENCY_EXCHANGE' && (
              <div className="form-group">
                <label>Moeda destino</label>
                <select value={transferForm.targetCurrency} onChange={e => setTransferForm({...transferForm, targetCurrency: e.target.value})}>
                  <option value="">Selecione a moeda...</option>
                  {currentAccount.currencies.filter(c => c !== transferForm.currency).map(c => (
                    <option key={c} value={c}>{CURRENCY_CONFIG[c]?.flag} {c} — {CURRENCY_CONFIG[c]?.name}</option>
                  ))}
                </select>
              </div>
            )}

            <div className="form-group">
              <label>Descrição (opcional)</label>
              <input
                type="text"
                placeholder="Ex: Pagamento aluguel"
                value={transferForm.description}
                onChange={e => setTransferForm({...transferForm, description: e.target.value})}
              />
            </div>

            <button
              className="btn-primary"
              onClick={handleTransfer}
              disabled={!transferForm.amount || loading}
            >
              {loading ? 'Processando...' : 'Confirmar operação'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
