import { CreditCard, Landmark, Wallet } from 'lucide-react';
import type { PaymentMethod } from '../lib/types';
import { humanize } from '../lib/format';

export function PaymentMethodIcon({ method }: { method: PaymentMethod }) {
  const Icon = method === 'CARD' ? CreditCard : method === 'ONLINE_BANKING' ? Landmark : Wallet;
  return (
    <span className="flex gap-1 items-center text-sm">
      <Icon size={15} /> {humanize(method)}
    </span>
  );
}