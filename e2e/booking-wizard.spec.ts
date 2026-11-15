import { test, expect } from "@playwright/test";

test.describe("Customer Booking Wizard - 4-Step Happy Path", () => {
  const BUSINESS_ID = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";
  const SERVICE_ID = "11111111-1111-1111-1111-111111111111";
  const STAFF_ID = "22222222-2222-2222-2222-222222222222";
  const RESERVATION_ID = "33333333-3333-3333-3333-333333333333";

  test.beforeEach(async ({ page }) => {
    // 1. Mock Business Tenant Details
    await page.route(`**/api/v1/businesses/${BUSINESS_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: BUSINESS_ID,
          name: "The Parisian Bistro & Wellness Spa",
          slug: "parisian-spa",
          timezone: "America/New_York",
          email: "contact@parisianspa.com",
          phone: "+12125550199",
          status: "ACTIVE",
        }),
      });
    });

    // 2. Mock Bookable Services Catalog
    await page.route(`**/api/v1/services?businessId=${BUSINESS_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            id: SERVICE_ID,
            businessId: BUSINESS_ID,
            name: "Deep Tissue Massage 60m",
            description: "Therapeutic firm pressure full body massage targeting deep muscle layers.",
            durationMinutes: 60,
            priceAmount: 85.0,
            currency: "USD",
            isActive: true,
          },
          {
            id: "service-facial-45m",
            businessId: BUSINESS_ID,
            name: "Hydrating Facial 45m",
            description: "Refreshing organic botanical facial treatment.",
            durationMinutes: 45,
            priceAmount: 65.0,
            currency: "USD",
            isActive: true,
          },
        ]),
      });
    });

    // 3. Mock Active Staff Members
    await page.route(`**/api/v1/staff?businessId=${BUSINESS_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            id: STAFF_ID,
            businessId: BUSINESS_ID,
            userId: "user-marcus",
            fullName: "Marcus Vance",
            jobTitle: "Senior Massage Therapist",
            bio: "Specializing in sports therapy and neuromuscular alignment.",
            isActive: true,
          },
          {
            id: "staff-elena",
            businessId: BUSINESS_ID,
            userId: "user-elena",
            fullName: "Elena Rostova",
            jobTitle: "Holistic Aesthetician",
            bio: "Certified European skincare specialist.",
            isActive: true,
          },
        ]),
      });
    });

    // 4. Mock Availability API
    await page.route(`**/api/businesses/${BUSINESS_ID}/availability*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            staffId: STAFF_ID,
            startInstant: "2026-11-20T14:00:00Z",
            endInstant: "2026-11-20T15:00:00Z",
            localStartTime: "2026-11-20T09:00:00-05:00",
            localEndTime: "2026-11-20T10:00:00-05:00",
            durationMinutes: 60,
            available: true,
          },
          {
            staffId: STAFF_ID,
            startInstant: "2026-11-20T15:30:00Z",
            endInstant: "2026-11-20T16:30:00Z",
            localStartTime: "2026-11-20T10:30:00-05:00",
            localEndTime: "2026-11-20T11:30:00-05:00",
            durationMinutes: 60,
            available: true,
          },
          {
            staffId: STAFF_ID,
            startInstant: "2026-11-20T18:00:00Z",
            endInstant: "2026-11-20T19:00:00Z",
            localStartTime: "2026-11-20T13:00:00-05:00",
            localEndTime: "2026-11-20T14:00:00-05:00",
            durationMinutes: 60,
            available: true,
          },
        ]),
      });
    });

    // 5. Mock Reservation Creation Hold Endpoint
    await page.route("**/api/reservations", async (route) => {
      if (route.request().method() === "POST") {
        const payload = JSON.parse(route.request().postData() || "{}");
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify({
            id: RESERVATION_ID,
            businessId: payload.businessId,
            customerId: payload.customerId,
            serviceId: payload.serviceId,
            staffId: payload.staffId,
            startTime: payload.startTime,
            endTime: "2026-11-20T15:00:00Z",
            status: "PENDING",
            totalPrice: 85.0,
            currency: "USD",
            holdExpiresAt: "2026-11-20T14:15:00Z",
            notes: payload.notes || null,
          }),
        });
      } else {
        await route.continue();
      }
    });

    // 6. Mock Stripe PaymentIntent Creation Endpoint
    await page.route(`**/api/reservations/${RESERVATION_ID}/payment-intent`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          clientSecret: "pi_3MtwBwLkdIwHu7ix28a3tqPa_secret_YrPnDX7qU91Xp23aBc991",
          paymentIntentId: "pi_3MtwBwLkdIwHu7ix28a3tqPa",
          amount: 85.0,
          currency: "USD",
          reservationId: RESERVATION_ID,
        }),
      });
    });
  });

  test("should complete the 4-step booking wizard with optimistic update and payment", async ({
    page,
  }) => {
    // Navigate to customer booking wizard
    await page.goto(`/book?businessId=${BUSINESS_ID}`);

    // Verify Wizard Root & Header
    await expect(page.getByTestId("booking-wizard-root")).toBeVisible();
    await expect(page.getByText("The Parisian Bistro & Wellness Spa")).toBeVisible();

    // -------------------------------------------------------------------------
    // STEP 1: CHOOSE SERVICE
    // -------------------------------------------------------------------------
    await expect(page.getByRole("heading", { name: "1. Select a Service" })).toBeVisible();

    // Verify next button is initially disabled
    const step1NextBtn = page.getByTestId("service-next-button");
    await expect(step1NextBtn).toBeDisabled();

    // Select "Deep Tissue Massage 60m"
    const serviceCard = page.getByTestId(`service-card-${SERVICE_ID}`);
    await expect(serviceCard).toBeVisible();
    await serviceCard.click();

    // Verify card is selected and next button is enabled
    await expect(serviceCard).toHaveAttribute("aria-checked", "true");
    await expect(step1NextBtn).toBeEnabled();
    await step1NextBtn.click();

    // -------------------------------------------------------------------------
    // STEP 2: CHOOSE STAFF (OR "ANY")
    // -------------------------------------------------------------------------
    await expect(page.getByRole("heading", { name: "2. Choose a Specialist" })).toBeVisible();

    // Select Marcus Vance
    const staffCard = page.getByTestId(`staff-card-${STAFF_ID}`);
    await expect(staffCard).toBeVisible();
    await staffCard.click();
    await expect(staffCard).toHaveAttribute("aria-checked", "true");

    const step2NextBtn = page.getByTestId("staff-next-button");
    await expect(step2NextBtn).toBeEnabled();
    await step2NextBtn.click();

    // -------------------------------------------------------------------------
    // STEP 3: CHOOSE DATE + SLOT (TIMEZONE EXPLICIT & 409 HANDLING)
    // -------------------------------------------------------------------------
    await expect(page.getByRole("heading", { name: "3. Choose Date & Time" })).toBeVisible();

    // Verify Operating Timezone is explicitly presented
    await expect(page.getByText(/Operating Timezone: America\/New_York/i)).toBeVisible();

    // Verify next button is disabled before slot selection
    const step3NextBtn = page.getByTestId("slot-next-button");
    await expect(step3NextBtn).toBeDisabled();

    // Choose 09:00 AM slot
    const slotChip = page.getByRole("radio", { name: /Appointment slot starting at/i }).first();
    await expect(slotChip).toBeVisible();
    await slotChip.click();
    await expect(slotChip).toHaveAttribute("aria-checked", "true");

    await expect(step3NextBtn).toBeEnabled();
    await step3NextBtn.click();

    // -------------------------------------------------------------------------
    // STEP 4: CONFIRM + STRIPE PAYMENT
    // -------------------------------------------------------------------------
    await expect(page.getByRole("heading", { name: "4. Review & Confirm Booking" })).toBeVisible();

    // Verify Summary Card values
    await expect(page.getByTestId("summary-service-name")).toHaveText("Deep Tissue Massage 60m");
    await expect(page.getByTestId("summary-staff-name")).toHaveText("Marcus Vance");
    await expect(page.getByTestId("summary-timezone")).toHaveText("America/New_York");
    await expect(page.getByTestId("summary-price")).toHaveText("$85.00");

    // Fill customer contact information
    await page.getByTestId("input-first-name").fill("Jane");
    await page.getByTestId("input-last-name").fill("Doe");
    await page.getByTestId("input-email").fill("jane.doe@example.com");
    await page.getByTestId("input-phone").fill("+1 (212) 555-0199");
    await page.getByTestId("input-notes").fill("No lavender scented oils please.");

    // Fill Stripe Payment fields
    await page.getByTestId("input-cardholder-name").fill("Jane Doe");
    await page.getByTestId("input-card-number").fill("4242 4242 4242 4242");
    await page.getByTestId("input-card-expiry").fill("12/28");
    await page.getByTestId("input-card-cvc").fill("123");

    // Submit booking confirmation
    const confirmButton = page.getByTestId("confirm-booking-button");
    await expect(confirmButton).toBeEnabled();
    await confirmButton.click();

    // -------------------------------------------------------------------------
    // SUCCESS CONFIRMATION SCREEN
    // -------------------------------------------------------------------------
    await expect(page.getByRole("heading", { name: "Reservation Confirmed!" })).toBeVisible();
    await expect(page.getByTestId("booking-reference-id")).toHaveText(RESERVATION_ID);
    await expect(page.getByText(/jane\.doe@example\.com/i)).toBeVisible();
    await expect(page.getByText("CONFIRMED")).toBeVisible();
    await expect(page.getByTestId("book-another-button")).toBeVisible();
  });
});

