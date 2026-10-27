package com.healthee.cart;

import com.healthee.cart.request.AddCartItemRequest;
import com.healthee.cart.request.UpdateCartItemRequest;
import com.healthee.cart.response.CartItemResponse;
import com.healthee.cart.response.CartResponse;
import com.healthee.customer.CustomerService;
import com.healthee.products.Product;
import com.healthee.products.ProductService;
import com.healthee.utils.exception.InvalidInputException;
import com.healthee.utils.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "transactionManager")
public class CartService {

    private static final int MAX_ITEM_QUANTITY = 100;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductService productService;
    private final CustomerService customerService;

    public CartResponse getCurrentCustomerCart() {
        Cart cart = getOrCreateCurrentCustomerCart();
        return buildCartResponse(cart);
    }

    public CartResponse addItem(AddCartItemRequest request) {
        if (request == null) {
            throw new InvalidInputException(
                    "Request body is required"
            );
        }

        validateQuantity(request.quantity());

        Product product = findAvailableProduct(
                request.productId()
        );

        Cart cart = getOrCreateCurrentCustomerCart();
        LocalDateTime updatedAt = LocalDateTime.now();

        CartItem cartItem = cart.getItems().stream()
                .filter(item ->
                        Objects.equals(
                                item.getProductId(),
                                product.getId()
                        )
                )
                .findFirst()
                .orElse(null);

        if (cartItem == null) {
            cartItem = new CartItem();

            cartItem.setId(UUID.randomUUID().toString());
            cartItem.setCartId(cart.getId());
            cartItem.setCustomerId(cart.getCustomerId());
            cartItem.setProductId(product.getId());
            cartItem.setQuantity(request.quantity());
            cartItem.setUpdatedAt(updatedAt);

            cart.addItem(cartItem);
        } else {
            int newQuantity =
                    cartItem.getQuantity() + request.quantity();

            validateQuantity(newQuantity);

            cartItem.setQuantity(newQuantity);
            cartItem.setUpdatedAt(updatedAt);
        }

        cart.setUpdatedAt(updatedAt);

        // Persist the separate item and its embedded copy in one transaction.
        cartItemRepository.save(cartItem);
        Cart savedCart = cartRepository.save(cart);

        return buildCartResponse(savedCart);
    }

    public CartResponse updateItem(
            String cartItemId,
            UpdateCartItemRequest request
    ) {
        if (request == null) {
            throw new InvalidInputException(
                    "Request body is required"
            );
        }

        validateQuantity(request.quantity());

        Cart cart = getOrCreateCurrentCustomerCart();
        CartItem cartItem = findCartItem(cartItemId, cart);

        findAvailableProduct(cartItem.getProductId());

        LocalDateTime updatedAt = LocalDateTime.now();

        cartItem.setQuantity(request.quantity());
        cartItem.setUpdatedAt(updatedAt);
        cart.setUpdatedAt(updatedAt);

        // Persist the separate item and its embedded copy in one transaction.
        cartItemRepository.save(cartItem);
        Cart savedCart = cartRepository.save(cart);

        return buildCartResponse(savedCart);
    }

    public CartResponse removeItem(String cartItemId) {
        Cart cart = getOrCreateCurrentCustomerCart();
        CartItem cartItem = findCartItem(cartItemId, cart);

        cartItemRepository.delete(cartItem);

        cart.removeItem(cartItem);
        cart.setUpdatedAt(LocalDateTime.now());

        Cart savedCart = cartRepository.save(cart);

        return buildCartResponse(savedCart);
    }

    public void clearCart() {
        Cart cart = getOrCreateCurrentCustomerCart();

        // Delete these items before clearing the embedded list.
        cartItemRepository.deleteAll(cart.getItems());

        cart.clearItems();
        cart.setUpdatedAt(LocalDateTime.now());

        cartRepository.save(cart);
    }

    private Cart getOrCreateCurrentCustomerCart() {
        String customerId =
                customerService.getCurrentProfile().customerId();

        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    Cart createdCart = new Cart();

                    createdCart.setId(
                            UUID.randomUUID().toString()
                    );

                    createdCart.setCustomerId(customerId);

                    return cartRepository.save(createdCart);
                });

        // Embedded items need explicit IDs and ownership fields.
        Set<String> repairedItemIds = new HashSet<>();

        for (CartItem item : cart.getItems()) {
            boolean repaired = false;

            if (item.getId() == null || item.getId().isBlank()) {
                item.setId(UUID.randomUUID().toString());
                repaired = true;
            }

            if (!Objects.equals(item.getCartId(), cart.getId())) {
                item.setCartId(cart.getId());
                repaired = true;
            }

            if (!Objects.equals(
                    item.getCustomerId(),
                    cart.getCustomerId()
            )) {
                item.setCustomerId(cart.getCustomerId());
                repaired = true;
            }

            if (repaired) {
                repairedItemIds.add(item.getId());
            }
        }

        if (!cart.getItems().isEmpty()) {
            List<String> itemIds = cart.getItems().stream()
                    .map(CartItem::getId)
                    .toList();

            Set<String> savedItemIds = new HashSet<>();

            cartItemRepository.findAllById(itemIds)
                    .forEach(item -> savedItemIds.add(item.getId()));

            // Backfill older embedded items using one lookup for the cart.
            List<CartItem> itemsToSave = cart.getItems().stream()
                    .filter(item ->
                            repairedItemIds.contains(item.getId()) ||
                                    !savedItemIds.contains(item.getId())
                    )
                    .toList();

            if (!itemsToSave.isEmpty()) {
                cartItemRepository.saveAll(itemsToSave);
            }
        }

        if (!repairedItemIds.isEmpty()) {
            cart = cartRepository.save(cart);
        }

        return cart;
    }

    private CartItem findCartItem(
            String cartItemId,
            Cart cart
    ) {
        if (
                cartItemId == null ||
                        cartItemId.isBlank()
        ) {
            throw new InvalidInputException(
                    "Cart item ID is required"
            );
        }

        return cart.getItems().stream()
                .filter(item ->
                        Objects.equals(
                                item.getId(),
                                cartItemId.trim()
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Cart item not found"
                        )
                );
    }

    private Product findAvailableProduct(String productId) {
        if (
                productId == null ||
                        productId.isBlank()
        ) {
            throw new InvalidInputException(
                    "Product ID is required"
            );
        }

        Product product = productService.findById(
                productId.trim()
        );

        if (!product.isAvailableForPublicDisplay()) {
            throw new InvalidInputException(
                    "Product is not available"
            );
        }

        return product;
    }

    private void validateQuantity(Integer quantity) {
        if (
                quantity == null ||
                        quantity < 1 ||
                        quantity > MAX_ITEM_QUANTITY
        ) {
            throw new InvalidInputException(
                    "Quantity must be between 1 and " +
                            MAX_ITEM_QUANTITY
            );
        }
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();

        int totalQuantity = 0;
        BigDecimal subtotal = BigDecimal.ZERO;
        String currencyCode = "NGN";

        for (CartItem cartItem : cart.getItems()) {
            Product product;

            try {
                product = productService.findById(
                        cartItem.getProductId()
                );
            } catch (ResourceNotFoundException ex) {
                product = null;
            }

            CartItemResponse itemResponse =
                    buildCartItemResponse(cartItem, product);

            items.add(itemResponse);
            totalQuantity += cartItem.getQuantity();

            if (product != null) {
                currencyCode = "NGN";

                if (itemResponse.available()) {
                    BigDecimal unitPrice =
                            product.getProductPrice() == null
                                    ? BigDecimal.ZERO
                                    : product.getProductPrice();

                    BigDecimal lineTotal = unitPrice.multiply(
                            BigDecimal.valueOf(
                                    cartItem.getQuantity()
                            )
                    );

                    subtotal = subtotal.add(lineTotal);
                }
            }
        }

        return new CartResponse(
                cart.getId(),
                cart.getCustomerId(),
                items,
                items.size(),
                totalQuantity,
                subtotal,
                currencyCode,
                cart.getUpdatedAt()
        );
    }

    private CartItemResponse buildCartItemResponse(
            CartItem cartItem,
            Product product
    ) {
        if (product == null) {
            return new CartItemResponse(
                    cartItem.getId(),
                    cartItem.getProductId(),
                    Set.of(),
                    "Unavailable product",
                    false,
                    cartItem.getQuantity(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null
            );
        }

        Set<String> images =
                product.getProductThumbnailsUrls() == null
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(
                        product.getProductThumbnailsUrls()
                );

        return new CartItemResponse(
                cartItem.getId(),
                product.getId(),
                images,
                product.getProductName(),
                product.isAvailableForPublicDisplay(),
                cartItem.getQuantity(),
                product.getProductPrice() == null
                        ? BigDecimal.ZERO
                        : product.getProductPrice(),
                product.isAvailableForPublicDisplay() &&
                        product.getProductPrice() != null
                        ? product.getProductPrice().multiply(
                        BigDecimal.valueOf(
                                cartItem.getQuantity()
                        )
                )
                        : BigDecimal.ZERO,
                product.getProductBottleSize()
        );
    }
}