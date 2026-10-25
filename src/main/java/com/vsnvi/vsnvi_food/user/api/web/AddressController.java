package com.vsnvi.vsnvi_food.user.api.web;

import com.vsnvi.vsnvi_food.user.application.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "user", description = "Profiles, addresses and admin user management")
class AddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "List the caller's addresses", operationId = "listMyAddresses")
    List<AddressResponse> list() {
        return addressService.list().stream().map(AddressResponse::from).toList();
    }

    @PostMapping
    @Operation(summary = "Add an address", operationId = "addMyAddress")
    ResponseEntity<AddressResponse> add(@Valid @RequestBody SaveAddressRequest request) {
        var address = addressService.add(request.toDetails());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(address.id())
                .toUri();
        return ResponseEntity.created(location).body(AddressResponse.from(address));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an address", operationId = "replaceMyAddress")
    AddressResponse replace(@PathVariable UUID id, @Valid @RequestBody SaveAddressRequest request) {
        return AddressResponse.from(addressService.replace(id, request.toDetails()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an address", operationId = "deleteMyAddress")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        addressService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
