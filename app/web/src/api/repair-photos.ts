import { authHeaders } from './client';
import { ApiError, NetworkError, backendUrl, toErrorBody } from './errors';

export async function fetchPhotoBlob(photoId: string, signal?: AbortSignal): Promise<Blob> {
  const url = backendUrl(`/api/v1/repair-photos/${encodeURIComponent(photoId)}/content`);
  let response: Response;
  try {
    response = await fetch(url, { headers: { Accept: 'image/jpeg', ...authHeaders() }, signal });
    if (!response.ok) throw new ApiError(response.status, toErrorBody(await response.text()));
    return await response.blob();
  } catch (cause) {
    if (cause instanceof ApiError) throw cause;
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new NetworkError(cause);
  }
}
