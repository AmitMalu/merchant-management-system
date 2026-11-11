package com.project2.ism.Service.AdminServices;

import com.project2.ism.Model.Users.Permission;
import com.project2.ism.Repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "Add Missing Modules": every sidebar permission ends up in the list, nothing that exists is touched.
 */
class PermissionDefaultsTest {

    private final Map<String, Permission> store = new HashMap<>();
    private PermissionService service;

    @BeforeEach
    void setUp() {
        PermissionRepository repo = mock(PermissionRepository.class);
        when(repo.findByName(anyString())).thenAnswer(i -> Optional.ofNullable(store.get(i.<String>getArgument(0))));
        when(repo.save(any(Permission.class))).thenAnswer(i -> {
            Permission p = i.getArgument(0);
            store.put(p.getName(), p);
            return p;
        });
        service = new PermissionService();
        ReflectionTestUtils.setField(service, "permissionRepository", repo);
    }

    @Test
    void anEmptyList_getsEveryModuleAndMenuItemTheSidebarUses() {
        List<String> added = service.addMissingDefaultPermissions();

        for (String name : List.of(
                "Dashboard", "Admin Management", "Logs", "Taxes Manage", "Edit History", "Wallet Adjustment", "My Permissions",
                "Vendors", "Vendor List", "Product List", "Vendor Rates",
                "Inventory Management", "Pricing Scheme", "Product Scheme Assign", "Inventory",
                "Customers", "Customer List", "Onboard Customer", "Merchant Approval", "Products Distribution", "Change Password",
                "Payment", "Payment Products", "Payment Charges", "Payment Vendors", "Payment Vendors Credentials",
                "Payment Vendor Routing",
                "Other", "File Upload", "Charge Calculation", "Batch Status", "Prefund Authorization",
                "Merchant Settings", "Update Transaction", "Notification URL",
                "Transaction Monitoring", "Support Tickets", "Reports")) {
            assertTrue(added.contains(name), "missing: " + name);
        }
        // menu items sit under their module
        assertEquals("Other", store.get("Notification URL").getParent().getName());
        assertEquals("Payment", store.get("Payment Vendor Routing").getParent().getName());
        assertNull(store.get("Other").getParent());
    }

    @Test
    void whatAlreadyExists_isLeftAlone_andOnlyTheMissingOnesAreAdded() {
        Permission mine = new Permission("Logs", "my own description");
        store.put("Logs", mine);
        Permission other = new Permission("Other", "custom");
        store.put("Other", other);

        List<String> added = service.addMissingDefaultPermissions();

        assertFalse(added.contains("Logs"));
        assertFalse(added.contains("Other"));
        assertTrue(added.contains("Notification URL"));
        assertSame(mine, store.get("Logs"));
        assertEquals("my own description", store.get("Logs").getDescription());
        assertEquals("custom", store.get("Other").getDescription());
        assertSame(other, store.get("Notification URL").getParent());   // new item goes under the existing module
    }

    @Test
    void runningItAgain_addsNothing() {
        service.addMissingDefaultPermissions();
        assertTrue(service.addMissingDefaultPermissions().isEmpty());
    }
}
