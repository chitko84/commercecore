package com.commercecore.user;

import com.commercecore.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class AddressService {
    private final AddressRepository addresses;
    private final UserAccountRepository users;
    public AddressService(AddressRepository addresses, UserAccountRepository users) {
        this.addresses = addresses; this.users = users;
    }

    @Transactional(readOnly = true)
    public List<UserDtos.AddressResponse> list(UUID userId) {
        return addresses.findAllByUserIdOrderByCreatedAtAsc(userId).stream().map(AddressService::response).toList();
    }

    @Transactional
    public UserDtos.AddressResponse create(UUID userId, UserDtos.AddressRequest request) {
        UserAccount user = users.findById(userId).orElseThrow(AddressService::accountNotFound);
        clearRequestedDefaults(userId, request.defaultShipping(), request.defaultBilling());
        Address address = new Address(user, request.label(), request.recipientName(), request.phone(),
                request.addressLine1(), request.addressLine2(), request.city(), request.stateRegion(),
                request.postalCode(), request.countryCode(), request.defaultShipping(), request.defaultBilling());
        return response(addresses.save(address));
    }

    @Transactional
    public UserDtos.AddressResponse update(UUID userId, UUID addressId, UserDtos.AddressRequest request) {
        Address address = ownAddress(userId, addressId);
        if (request.defaultShipping()) addresses.clearOtherDefaultShipping(userId, addressId);
        if (request.defaultBilling()) addresses.clearOtherDefaultBilling(userId, addressId);
        address.update(request.label(), request.recipientName(), request.phone(), request.addressLine1(),
                request.addressLine2(), request.city(), request.stateRegion(), request.postalCode(),
                request.countryCode(), request.defaultShipping(), request.defaultBilling());
        return response(address);
    }

    @Transactional public void delete(UUID userId, UUID addressId) { addresses.delete(ownAddress(userId, addressId)); }

    @Transactional
    public UserDtos.AddressResponse makeDefaultShipping(UUID userId, UUID addressId) {
        Address address = ownAddress(userId, addressId);
        addresses.clearOtherDefaultShipping(userId, addressId);
        address.makeDefaultShipping();
        return response(address);
    }

    @Transactional
    public UserDtos.AddressResponse makeDefaultBilling(UUID userId, UUID addressId) {
        Address address = ownAddress(userId, addressId);
        addresses.clearOtherDefaultBilling(userId, addressId);
        address.makeDefaultBilling();
        return response(address);
    }

    private void clearRequestedDefaults(UUID userId, boolean shipping, boolean billing) {
        if (shipping) addresses.clearDefaultShipping(userId);
        if (billing) addresses.clearDefaultBilling(userId);
    }
    private Address ownAddress(UUID userId, UUID addressId) {
        return addresses.findByIdAndUserId(addressId, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "The address was not found."));
    }
    private static ApiException accountNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "The account was not found.");
    }
    private static UserDtos.AddressResponse response(Address a) {
        return new UserDtos.AddressResponse(a.getId(), a.getLabel(), a.getRecipientName(), a.getPhone(),
                a.getAddressLine1(), a.getAddressLine2(), a.getCity(), a.getStateRegion(), a.getPostalCode(),
                a.getCountryCode(), a.isDefaultShipping(), a.isDefaultBilling(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
