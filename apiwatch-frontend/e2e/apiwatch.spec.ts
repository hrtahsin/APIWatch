import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

async function signIn(page: Page, username: string) {
  await page.goto('/login')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill('acceptance-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'System overview' })).toBeVisible()
}

async function expectNoSeriousAccessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()

  const blockingViolations = results.violations.filter(
    ({ impact }) => impact === 'serious' || impact === 'critical',
  )
  expect(blockingViolations).toEqual([])
}

test('administrator can navigate the operational workspace', async ({ page }) => {
  await signIn(page, 'acceptance-admin')

  await expect(page.getByText('Administrator', { exact: true })).toBeVisible()
  await expect(page.getByText('Payments API', { exact: true }).first()).toBeVisible()

  await page.getByRole('link', { name: 'Services', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: 'Monitored services', exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('link', { name: 'Add service' }).first()).toBeVisible()

  await page.getByRole('link', { name: 'Incidents', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Incident timeline' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Incident history' })).toBeVisible()

  await page.getByRole('link', { name: 'Settings', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Notification settings' })).toBeVisible()
})

test('viewer remains read-only and cannot open administrator routes', async ({ page }) => {
  await signIn(page, 'acceptance-viewer')

  await expect(page.getByText('Read-only viewer', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Add service' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Settings' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Audit logs' })).toHaveCount(0)

  await page.goto('/settings')
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { name: 'System overview' })).toBeVisible()
})

test('critical user surfaces meet the automated accessibility gate', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)

  await signIn(page, 'accessibility-admin')
  await expectNoSeriousAccessibilityViolations(page)

  await page.getByRole('link', { name: 'Services', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: 'Monitored services', exact: true }),
  ).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)

  await page.getByRole('link', { name: 'Incidents', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Incident timeline' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
})
