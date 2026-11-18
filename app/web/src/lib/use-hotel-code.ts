import { useParams } from 'react-router';
import { HOTEL_CODE_PARAM } from './paths';

/**
 * 目前網址上的飯店識別代碼。櫃檯畫面的每一頁都掛在 `/{飯店識別代碼}/...` 底下（見 `router.tsx`），
 * 所以在這些頁面裡一定拿得到；拿不到代表有畫面被掛到沒有代碼的路徑上，是程式寫錯，直接拋錯。
 */
export function useHotelCode(): string {
  const hotelCode = useParams()[HOTEL_CODE_PARAM];
  if (hotelCode === undefined || hotelCode === '') {
    throw new Error('這個畫面要掛在帶飯店識別代碼的路徑底下');
  }
  return hotelCode;
}
