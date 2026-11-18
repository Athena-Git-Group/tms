import { useParams } from 'react-router';
import { HOTEL_CODE_PARAM } from './paths';

export function useHotelCode(): string {
  const hotelCode = useParams()[HOTEL_CODE_PARAM];
  if (hotelCode === undefined || hotelCode === '') {
    throw new Error('這個畫面要掛在帶飯店識別代碼的路徑底下');
  }
  return hotelCode;
}
