import type { Metadata } from 'next';
import './globals.css';
export const metadata: Metadata = { title: 'ZEM 2.0 · Espace conducteur', description: 'Dossier conducteur et vérification de carte numérique ZEM' };
export default function Layout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="fr"><body>{children}</body></html>;
}
