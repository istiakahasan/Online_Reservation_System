import { apiClient } from "../client.ts";
import type {
  CreateReservationRequest,
  ReservationResponse,
  PaymentIntentResponse,
} from "../types.ts";

export interface CancelReservationParams {
  id: string;
  actorId: string;
  reason?: string;
}

export const reservationsApi = {
  /**
   * Creates a reservation hold pending payment (authenticated).
   */
  async createReservation(request: CreateReservationRequest): Promise<ReservationResponse> {
    return apiClient.post<ReservationResponse>("/api/reservations", request);
  },

  /**
   * Retrieves reservation by UUID.
   */
  async getById(id: string): Promise<ReservationResponse> {
    return apiClient.get<ReservationResponse>(`/api/reservations/${id}`);
  },

  /**
   * Retrieves all reservations belonging to a customer.
   */
  async getByCustomer(customerId: string): Promise<ReservationResponse[]> {
    return apiClient.get<ReservationResponse[]>(`/api/reservations/customer/${customerId}`);
  },

  /**
   * Creates a Stripe PaymentIntent tied to the reservation and returns clientSecret.
   */
  async createPaymentIntent(id: string): Promise<PaymentIntentResponse> {
    return apiClient.post<PaymentIntentResponse>(`/api/reservations/${id}/payment-intent`);
  },

  /**
   * Cancels an active or confirmed reservation with cancellation and refund policies.
   */
  async cancelReservation(params: CancelReservationParams): Promise<ReservationResponse> {
    const { id, ...queryParams } = params;
    return apiClient.put<ReservationResponse>(
      `/api/reservations/${id}/cancel`,
      undefined,
      { params: queryParams }
    );
  },
};
