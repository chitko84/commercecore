import { expect, test, type Page } from '@playwright/test';

const accounts = {
  customer: { email: 'demo@commercecore.dev', password: 'DemoPass!2026' },
  staff: { email: 'staff@commercecore.dev', password: 'StaffPass!2026' },
  admin: { email: 'admin@commercecore.dev', password: 'AdminPass!2026' },
};

async function login(page: Page, account: { email: string; password: string }) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/(account|admin)/);
}

async function expectNoHorizontalOverflow(page: Page, label: string) {
  const result = await page.evaluate(() => {
    const overflow = document.documentElement.scrollWidth - document.documentElement.clientWidth;
    const offenders = Array.from(document.querySelectorAll<HTMLElement>('body *'))
      .filter((element) => {
        const rect = element.getBoundingClientRect();
        return rect.width > 0 && (rect.right > window.innerWidth + 1 || rect.left < -1);
      })
      .slice(0, 5)
      .map((element) => `${element.tagName.toLowerCase()}.${element.className}`);
    return { overflow, offenders };
  });
  expect(result.overflow, `${label}; overflowing elements: ${result.offenders.join(', ')}`).toBeLessThanOrEqual(1);
  expect(result.offenders, `${label}; elements extend beyond viewport`).toEqual([]);
}

test.describe.configure({ mode: 'serial' });

test('application loads and the public storefront is responsive at all target viewports', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });

  const viewports = [
    { width: 320, height: 568 },
    { width: 375, height: 667 },
    { width: 390, height: 844 },
    { width: 768, height: 1024 },
    { width: 1366, height: 768 },
    { width: 1920, height: 1080 },
  ];
  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Technology');
    await expect(page.locator('img[alt="Premium over-ear headphones on a dark background"]')).toBeVisible();
    await expectNoHorizontalOverflow(page, `home overflow at ${viewport.width}x${viewport.height}`);
  }


  for (const viewport of [{ width: 320, height: 568 }, { width: 375, height: 667 }, { width: 768, height: 1024 }]) {
    await page.setViewportSize(viewport);
    for (const route of ['/catalog', '/products/bassline-portable-speaker', '/login']) {
      await page.goto(route);
      await expect(page.locator('main')).toBeVisible();
      await expectNoHorizontalOverflow(page, `${route} overflow at ${viewport.width}x${viewport.height}`);
    }
  }

  await page.setViewportSize({ width: 320, height: 568 });
  await page.goto('/');
  await page.getByRole('button', { name: 'Open menu' }).click();
  const drawer = page.getByRole('dialog', { name: 'Navigation' });
  await expect(drawer).toBeVisible();
  await expect(drawer.getByRole('link', { name: 'Home' })).toBeVisible();
  await expect(drawer.getByRole('link', { name: 'Shop', exact: true })).toBeVisible();
  await expect(drawer.getByRole('heading', { name: 'Account' })).toBeVisible();
  await expect(drawer.getByRole('heading', { name: 'Admin' })).toHaveCount(0);
  const drawerWidth = await drawer.evaluate((element) => element.getBoundingClientRect().width);
  expect(drawerWidth).toBeLessThanOrEqual(320);
  expect(drawerWidth).toBeLessThanOrEqual(320 * 0.85 + 1);
  await page.getByRole('button', { name: 'Close menu' }).click();

  await page.goto('/catalog?q=Aurora');
  await expect(page.getByText('Aurora Wireless Headphones')).toBeVisible();
  expect(consoleErrors).toEqual([]);
});

test('failed login is handled without creating a session', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill(accounts.customer.email);
  await page.getByLabel('Password').fill('DefinitelyWrong!2026');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.locator('.form-error')).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/account/orders');
  await expect(page).toHaveURL(/\/login$/);
});

test('protected customer route returns to the requested URL after login and blocks admin UI', async ({ page }) => {
  await page.goto('/account/orders');
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('Email').fill(accounts.customer.email);
  await page.getByLabel('Password').fill(accounts.customer.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/account\/orders$/);
  await expect(page.getByRole('heading', { name: 'Orders' })).toBeVisible();

  await page.goto('/admin');
  await expect(page.getByText('Access denied')).toBeVisible();
});

test('customer completes a real cart, promotion, checkout and sandbox payment flow', async ({ page }) => {
  await login(page, accounts.customer);
  await page.evaluate(async () => {
    const token = sessionStorage.getItem('commercecore.access_token');
    const headers = { Authorization: `Bearer ${token}` };
    const response = await fetch('/api/v1/cart', {
      method: 'DELETE',
      headers,
    });
    if (!response.ok) throw new Error(`Unable to reset E2E cart: ${response.status}`);
    const promotionResponse = await fetch('/api/v1/cart/promotion', { method: 'DELETE', headers });
    if (!promotionResponse.ok) throw new Error(`Unable to reset E2E promotion: ${promotionResponse.status}`);
  });
  const forbiddenStatus = await page.evaluate(async () => {
    const token = sessionStorage.getItem('commercecore.access_token');
    return (await fetch('/api/v1/admin/users', {
      headers: { Authorization: `Bearer ${token}` },
    })).status;
  });
  expect(forbiddenStatus).toBe(403);

  await page.goto('/products/bassline-portable-speaker');
  await expect(page.getByRole('heading', { name: 'Bassline Portable Speaker' })).toBeVisible();
  await page.getByRole('button', { name: /Add to cart/ }).click();
  await expect(page.getByText('Added to cart')).toBeVisible();

  await page.goto('/cart');
  await expect(page.getByText('Bassline Portable Speaker')).toBeVisible();
  for (const viewport of [{ width: 320, height: 568 }, { width: 375, height: 667 }, { width: 768, height: 1024 }]) {
    await page.setViewportSize(viewport);
    await expectNoHorizontalOverflow(page, `cart overflow at ${viewport.width}x${viewport.height}`);
  }
  await page.getByLabel('Coupon code').fill('SAVE10');
  await page.getByRole('button', { name: 'Apply' }).click();
  await expect(page.getByText('SAVE10', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /Proceed to checkout/ }).click();

  await expect(page.getByRole('heading', { name: 'Checkout' })).toBeVisible();
  await expect(page.getByText(/Sandbox payments/)).toBeVisible();
  for (const viewport of [{ width: 320, height: 568 }, { width: 375, height: 667 }, { width: 768, height: 1024 }]) {
    await page.setViewportSize(viewport);
    await expectNoHorizontalOverflow(page, `checkout overflow at ${viewport.width}x${viewport.height}`);
  }
  await page.getByRole('button', { name: /Place order/ }).click();
  await expect(page.getByRole('heading', { name: 'Order confirmed' })).toBeVisible({ timeout: 30_000 });

  await page.goto('/account/orders');
  await expect(page.getByText('Confirmed', { exact: true }).first()).toBeVisible();
  await page.waitForTimeout(2_000);
  await page.goto('/account/notifications');
  await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible();
  await expect(page.locator('.card').filter({ hasText: /order|payment/i }).first()).toBeVisible({ timeout: 20_000 });
});

test('delivered-purchase review is moderated and becomes public', async ({ browser }) => {
  const reviewTitle = 'Phase 10 verified sound';
  const customerContext = await browser.newContext();
  const customer = await customerContext.newPage();
  await login(customer, accounts.customer);
  await customer.evaluate(async () => {
    const token = sessionStorage.getItem('commercecore.access_token');
    const headers = { Authorization: `Bearer ${token}` };
    const response = await fetch('/api/v1/me/reviews?size=100', { headers });
    if (!response.ok) throw new Error(`Unable to inspect E2E reviews: ${response.status}`);
    const reviews = await response.json() as { content: Array<{ id: string; productSlug: string }> };
    for (const review of reviews.content.filter((item) => item.productSlug === 'aurora-headphones')) {
      const deleted = await fetch(`/api/v1/me/reviews/${review.id}`, { method: 'DELETE', headers });
      if (!deleted.ok) throw new Error(`Unable to reset E2E review: ${deleted.status}`);
    }
  });
  await customer.goto('/products/aurora-headphones');
  await customer.getByLabel('Title').fill(reviewTitle);
  await customer.getByRole('textbox', { name: 'Review', exact: true })
    .fill('Excellent comfort and clear audio in the verified browser flow.');
  await customer.getByRole('button', { name: 'Submit review' }).click();
  await expect(customer.getByText('Review submitted for moderation')).toBeVisible();
  await customerContext.close();

  const staffContext = await browser.newContext();
  const staff = await staffContext.newPage();
  await login(staff, accounts.staff);
  await staff.goto('/admin/reviews');
  const review = staff.locator('.card').filter({ hasText: reviewTitle });
  await expect(review).toBeVisible();
  await review.getByRole('button', { name: 'Approve' }).click();
  await expect(staff.getByText('Review moderated')).toBeVisible();
  await staff.goto('/admin/users');
  await expect(staff.getByText('Access denied')).toBeVisible();
  await staffContext.close();

  const publicContext = await browser.newContext();
  const publicPage = await publicContext.newPage();
  await publicPage.goto('/products/aurora-headphones');
  await expect(publicPage.getByText(reviewTitle)).toBeVisible();
  await publicContext.close();
});

test('admin dashboard and privileged operational routes load through the gateway', async ({ page }) => {
  await login(page, accounts.admin);
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await expect(page.getByText('Net revenue')).toBeVisible();

  const routes = [
    ['/admin/users', 'User management'],
    ['/admin/inventory', 'Inventory'],
    ['/admin/orders', 'Orders'],
    ['/admin/reviews', 'Review moderation'],
    ['/admin/audit', 'Audit log'],
    ['/admin/reports', 'Reports'],
  ] as const;
  for (const [path, heading] of routes) {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: heading })).toBeVisible();
  }

  for (const viewport of [{ width: 320, height: 568 }, { width: 375, height: 667 }, { width: 768, height: 1024 }, { width: 1920, height: 1080 }]) {
    await page.setViewportSize(viewport);
    await page.goto('/admin');
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expectNoHorizontalOverflow(page, `admin overflow at ${viewport.width}x${viewport.height}`);
  }
});

test('logout clears the session and protects direct URL access', async ({ page }) => {
  await login(page, accounts.customer);
  await page.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: /Sign out/ }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible();

  await page.goto('/account');
  await expect(page).toHaveURL(/\/login$/);
});
