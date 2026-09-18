import { readFileSync } from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, serverTimestamp, Timestamp } from 'firebase/firestore';

let env;
before(async () => {
  // A demo project and explicit loopback host prevent tests from touching live data.
  env = await initializeTestEnvironment({
    projectId: 'demo-beanloyal-rules',
    // The single deployment source lives in the backend repository beside firebase.json and
    // firestore.indexes.json. This repository deliberately keeps no copy: two files drifted apart
    // once already, and the divergence was only caught by reading both by hand.
    firestore: { host: '127.0.0.1', port: 8088,
      rules: readFileSync(new URL('../../../bean_backedn/firestore.rules', import.meta.url), 'utf8') }
  });
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => env.clearFirestore());
const customer = () => env.authenticatedContext('alice').firestore();
const profile = () => ({ uid: 'alice', email: 'alice@example.test', fullName: '', birthday: '',
  gender: '', phone: '', address: '', profileComplete: false,
  createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
async function seed(path, data) {
  await env.withSecurityRulesDisabled(ctx => setDoc(doc(ctx.firestore(), path), data));
}

test('sign-in can create its own profile with server timestamps', async () => {
  await assertSucceeds(setDoc(doc(customer(), 'users/alice'), profile()));
});

for (const [field, value] of Object.entries({ points: 0, visits: 0, isVerified: true,
  role: 'admin', balance: 1000, lastEarnAt: Timestamp.fromMillis(0) })) {
  test(`creation rejects client-owned ${field}`, async () => {
    await assertFails(setDoc(doc(customer(), 'users/alice'), { ...profile(), [field]: value }));
  });
}

test('creation rejects forged identity and timestamps', async () => {
  for (const patch of [{ uid: 'bob' }, { createdAt: Timestamp.fromMillis(0) },
    { updatedAt: Timestamp.fromMillis(0) }, { profileComplete: 'true' }]) {
    await assertFails(setDoc(doc(customer(), 'users/alice'), { ...profile(), ...patch }));
  }
});

test('profile completion preserves backend balances', async () => {
  await seed('users/alice', { ...profile(), points: 200, visits: 3, isVerified: true });
  await assertSucceeds(updateDoc(doc(customer(), 'users/alice'), {
    fullName: 'Alice', birthday: '1995-01-01', gender: 'Female', phone: '+212612345678',
    address: 'Casablanca', profileComplete: true, updatedAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(doc(customer(), 'users/alice')));
});

test('updates cannot mutate balances, verification, role or identity', async () => {
  await seed('users/alice', { ...profile(), points: 200, visits: 3, isVerified: true });
  for (const patch of [{ points: 999 }, { visits: 99 }, { isVerified: false },
    { role: 'admin' }, { uid: 'bob' }, { lastEarnAt: Timestamp.fromMillis(0) },
    { createdAt: Timestamp.fromMillis(0) }, { updatedAt: Timestamp.fromMillis(0) }]) {
    await assertFails(updateDoc(doc(customer(), 'users/alice'), patch));
  }
});

test('other accounts and signed-out callers cannot access profiles', async () => {
  await seed('users/alice', profile());
  for (const db of [env.authenticatedContext('bob').firestore(), env.unauthenticatedContext().firestore()]) {
    await assertFails(getDoc(doc(db, 'users/alice')));
    await assertFails(updateDoc(doc(db, 'users/alice'), { fullName: 'Changed' }));
    await assertFails(setDoc(doc(db, 'users/alice-new'), { fullName: 'Changed' }));
  }
  await assertFails(deleteDoc(doc(customer(), 'users/alice')));
});

test('earn and redeem codes cannot be fetched, enumerated, or written', async () => {
  for (const path of ['earn_codes', 'redeem_codes']) {
    await seed(`${path}/secret`, { uid: 'alice', userUid: 'alice', status: 'pending' });
    await assertFails(getDoc(doc(customer(), `${path}/secret`)));
    await assertFails(getDocs(collection(customer(), path)));
    await assertFails(setDoc(doc(customer(), `${path}/new`), { status: 'pending' }));
  }
});

test('own activity is readable but cannot be forged', async () => {
  await seed('users/alice/activities/earn', { type: 'earn', delta: 20 });
  await assertSucceeds(getDoc(doc(customer(), 'users/alice/activities/earn')));
  await assertFails(setDoc(doc(customer(), 'users/alice/activities/fake'), { delta: 999 }));
  await assertFails(getDoc(doc(env.authenticatedContext('bob').firestore(), 'users/alice/activities/earn')));
});

test('catalog/config reads work; client edits are denied', async () => {
  for (const path of ['menu_items/coffee', 'rewards_catalog/coffee', 'config/home_banner', 'meta/app_status']) {
    await seed(path, { active: true });
    await assertSucceeds(getDoc(doc(customer(), path)));
    await assertFails(updateDoc(doc(customer(), path), { active: false }));
  }
});
