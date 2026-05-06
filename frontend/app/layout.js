import './globals.css';

export const metadata = {
  title: 'BankStatement — Extrato Multimoeda',
  description: 'Sistema de extrato bancário multiconta e multimoeda com microsserviços',
};

export default function RootLayout({ children }) {
  return (
    <html lang="pt-BR">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet" />
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
        <meta name="theme-color" content="#1a237e" />
      </head>
      <body>{children}</body>
    </html>
  );
}
