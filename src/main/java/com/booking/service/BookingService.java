package com.booking.service;

import com.booking.advice.MeetingRoomNotBookedException;
import com.booking.advice.ResourceNotFoundException;
import com.booking.models.Booking;
import com.booking.models.MeetingRoom;
import com.booking.models.TypeEvent;
import com.booking.models.User;
import com.booking.repositories.BookingRepo;
import com.booking.repositories.UserRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service

public class BookingService {
    private final BookingRepo bookingRepo;
    private  final UserRepo userRepo;

    public BookingService(BookingRepo bookingRepo, UserRepo userRepo) {
        this.bookingRepo = bookingRepo;
        this.userRepo = userRepo;
    }

    public List<Booking> getBookingsFromPreviousDay() {
        return bookingRepo.findBookingByStartTimeAfterOrderByStartTime(LocalDateTime.now().minusDays(1), PageRequest.of(0, 2));
    }

    public Booking getBookingById(Long bookingId) {
        return bookingRepo.findById(bookingId).orElseThrow(() -> new ResourceNotFoundException("Booking with ID " + bookingId + " doesn't exist."));
    }

    public Booking createBooking(Booking booking, Authentication authentication) {
        if (allChecks(booking)) {
            booking.setEmployee(userRepo.findByUsername(authentication.getName()));
            bookingRepo.save(booking);
        }
        return booking;
    }
    public Booking updateBooking(Long bookingId, Booking booking,  Authentication authentication) {
        Booking bookingOld =getBookingById(bookingId);
        if (bookingOld.getEmployee().getUsername().equals(authentication.getName())) {
            bookingOld.setTitle(booking.getTitle());
            bookingOld.setEmployee(userRepo.findByUsername(authentication.getName()));
            bookingOld.setStartTime(booking.getStartTime());
            bookingOld.setFinishTime(booking.getFinishTime());
            bookingOld.setMeetingRoom(booking.getMeetingRoom());
            bookingOld.setTypeEvent(booking.getTypeEvent());
            if (allChecks(bookingOld)) {
                bookingRepo.save(bookingOld);
            }
        }
        else throw new AccessDeniedException("You cannot change this booking.");
        return bookingOld;
    }

    public void deleteBookingById(Long bookingId, Authentication authentication) {
        boolean hasRoleAdmin =authentication.getAuthorities().stream().anyMatch(s -> s.getAuthority().equals("ROLE_ADMIN"));
        Booking booking = getBookingById(bookingId);
        if (booking.getEmployee().getUsername().equals(authentication.getName()) || hasRoleAdmin) {
            bookingRepo.delete(booking);
            }
        else throw new AccessDeniedException("You cannot change this booking.");
    }


    private boolean allChecks(Booking booking) {
        isMeetingRoomWorking(booking.getMeetingRoom());//работает ли комната
        checkTimeIsItWorkingTime(booking.getMeetingRoom(), booking.getStartTime(), booking.getFinishTime()); //рабочее ли время комнаты выбрано
        checkSuitableEvent(booking.getTypeEvent(), booking.getMeetingRoom()); // можно ты проводить выбранное событие в этой выбранной комнате
        checkStartBeforeAfter(booking.getStartTime(), booking.getFinishTime());
        checkRoomIsFreeInSelectedTime(booking.getId(), booking.getStartTime(), booking.getFinishTime());
        return true;
    }

    private void isMeetingRoomWorking(MeetingRoom meetingRoom) {
        if (!meetingRoom.isWorking()) {
            throw new MeetingRoomNotBookedException("Unfortunately, the meeting room is not working at the moment.");
        }
    }

    private void checkTimeIsItWorkingTime(MeetingRoom meetingRoom, LocalDateTime startTime, LocalDateTime finishTime) {
        LocalTime stWork = meetingRoom.getWorkTimeWith();
        LocalTime fnsWork = meetingRoom.getWorkTimeBy();
        LocalDateTime startWork = stWork.atDate(startTime.toLocalDate());
        LocalDateTime finishWork = fnsWork.atDate(startTime.toLocalDate());
        if (!((startTime.isAfter(startWork) || startTime.equals(startWork)) && (finishTime.isAfter(startWork)) &&
                (startTime.isBefore(finishWork)) && (finishTime.isBefore(finishWork) || finishTime.equals(finishWork)))) {
            throw new MeetingRoomNotBookedException("At this time, the meeting room does not work.");
        }
    }

    private void checkSuitableEvent(TypeEvent typeEvent, MeetingRoom meetingRoom) {
        boolean result = meetingRoom.getTypeEventSet().contains(typeEvent);
        if (!result) {
            throw new MeetingRoomNotBookedException("It is not possible to hold a '" + typeEvent + "' event in room " + meetingRoom.getTitle() + ".");
        }
    }

    private void checkRoomIsFreeInSelectedTime(Long bookingId, LocalDateTime startTimeReserve, LocalDateTime finishTimeReserve) {
        LocalDateTime startOfDay = startTimeReserve.toLocalDate().atStartOfDay().minusDays(1);
        LocalDateTime finishOfDay = startTimeReserve.toLocalDate().atStartOfDay().plusDays(1);
        List<Booking> bookingList = bookingRepo.findBookingByStartTimeBetweenOrderByStartTime(startOfDay, finishOfDay);
        bookingList.removeIf((booking) -> booking.getId().equals(bookingId));// удалять бронирование из списка найденных, для работы метода update, так как будет возникать конфликт самим с собой
        for (Booking booking : bookingList) {
            if (startTimeReserve.isBefore(booking.getFinishTime()) && finishTimeReserve.isAfter(booking.getStartTime())) {
                throw new MeetingRoomNotBookedException("It is impossible to book for this time, because there was an " +
                        "overlap with another booking");
            }
        }
    }

    private void checkStartBeforeAfter(LocalDateTime startTime, LocalDateTime finishTime) {
        if (startTime.isAfter(finishTime)) {
            throw new MeetingRoomNotBookedException("The time of booking is incorrect");
        }
    }

}