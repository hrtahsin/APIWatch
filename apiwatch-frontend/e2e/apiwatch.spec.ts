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

test('administrator receives clear mutation feedback and guarded destructive actions', async ({ page }) => {
  await signIn(page, 'feedback-admin')

  await page.getByRole('link', { name: 'Services', exact: true }).click()
  await page.getByRole('button', { name: 'Pause Payments API' }).click()
  await expect(page.getByRole('status')).toContainText('Monitoring paused')

  await page.goto('/services/1')
  await page.getByRole('button', { name: 'Delete service' }).click()
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('button', { name: 'Cancel' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
})

test('service form reports field-level validation errors', async ({ page }) => {
  await signIn(page, 'validation-admin')
  await page.goto('/services/new')

  await page.getByLabel('Expected status from').fill('500')
  await page.getByLabel('Expected status through').fill('200')
  await page.getByRole('button', { name: 'Save service' }).click()

  await expect(page.getByRole('alert')).toContainText('Review the highlighted fields')
  await expect(page.getByText('Minimum status cannot exceed the maximum.')).toBeVisible()
  await expect(page.getByLabel('Expected status from')).toHaveAttribute('aria-invalid', 'true')
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

  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: 'Notification settings' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)

  await page.goto('/services/new')
  await expect(page.getByRole('heading', { name: 'Add a service' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
})

test('mobile navigation and data tables adapt without page overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await signIn(page, 'mobile-admin')

  const menuButton = page.getByRole('button', { name: 'Open menu' })
  await expect(menuButton).toHaveAttribute('aria-expanded', 'false')
  await menuButton.click()
  await expect(menuButton).toHaveAttribute('aria-expanded', 'true')
  await expect(page.getByRole('button', { name: 'Close menu' })).toBeFocused()

  await page.getByRole('link', { name: 'Services', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: 'Monitored services', exact: true }),
  ).toBeVisible()

  const layout = await page.evaluate(() => {
    const table = document.querySelector('.mobile-card-table')
    return {
      pageOverflows: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      tableOverflows: table ? table.scrollWidth > table.clientWidth : true,
    }
  })
  expect(layout).toEqual({ pageOverflows: false, tableOverflows: false })
})
