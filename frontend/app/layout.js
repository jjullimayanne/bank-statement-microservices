import './globals.css';

export const metadata = {
  title: 'Extrato Bancário - Multimoeda & Multiconta',
  description: 'Sistema de extrato bancário com microsserviços e Saga Orquestrado',
};

export default function RootLayout({ children }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
