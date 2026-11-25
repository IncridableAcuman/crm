package com.savdocrm.modules.catalog.web.mapper;

import com.savdocrm.modules.catalog.application.dto.AddBarcodeCommand;
import com.savdocrm.modules.catalog.application.dto.BarcodeInput;
import com.savdocrm.modules.catalog.application.dto.CategoryDto;
import com.savdocrm.modules.catalog.application.dto.CategoryTreeDto;
import com.savdocrm.modules.catalog.application.dto.CreateCategoryCommand;
import com.savdocrm.modules.catalog.application.dto.CreateProductCommand;
import com.savdocrm.modules.catalog.application.dto.CreateUnitCommand;
import com.savdocrm.modules.catalog.application.dto.CreateVariantCommand;
import com.savdocrm.modules.catalog.application.dto.MoveCategoryCommand;
import com.savdocrm.modules.catalog.application.dto.ProductBarcodeDto;
import com.savdocrm.modules.catalog.application.dto.ProductDetailDto;
import com.savdocrm.modules.catalog.application.dto.ProductDto;
import com.savdocrm.modules.catalog.application.dto.ProductSummaryDto;
import com.savdocrm.modules.catalog.application.dto.ProductVariantDto;
import com.savdocrm.modules.catalog.application.dto.UnitOfMeasureDto;
import com.savdocrm.modules.catalog.application.dto.UpdateBarcodeCommand;
import com.savdocrm.modules.catalog.application.dto.UpdateCategoryCommand;
import com.savdocrm.modules.catalog.application.dto.UpdateProductCommand;
import com.savdocrm.modules.catalog.application.dto.UpdateUnitCommand;
import com.savdocrm.modules.catalog.application.dto.UpdateVariantCommand;
import com.savdocrm.modules.catalog.application.facade.VariantCatalogDto;
import com.savdocrm.modules.catalog.web.dto.request.AddBarcodeRequest;
import com.savdocrm.modules.catalog.web.dto.request.BarcodeInputRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateProductRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateUnitRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateVariantRequest;
import com.savdocrm.modules.catalog.web.dto.request.MoveCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateBarcodeRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateProductRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateUnitRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateVariantRequest;
import com.savdocrm.modules.catalog.web.dto.response.CategoryResponse;
import com.savdocrm.modules.catalog.web.dto.response.CategorySummaryResponse;
import com.savdocrm.modules.catalog.web.dto.response.CategoryTreeResponse;
import com.savdocrm.modules.catalog.web.dto.response.ProductBarcodeResponse;
import com.savdocrm.modules.catalog.web.dto.response.ProductResponse;
import com.savdocrm.modules.catalog.web.dto.response.ProductSummaryResponse;
import com.savdocrm.modules.catalog.web.dto.response.ProductVariantResponse;
import com.savdocrm.modules.catalog.web.dto.response.UnitOfMeasureResponse;
import com.savdocrm.modules.catalog.web.dto.response.UnitSummaryResponse;
import com.savdocrm.modules.catalog.web.dto.response.VariantLookupResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CatalogWebMapper {

    CreateProductCommand toCommand(CreateProductRequest request);

    UpdateProductCommand toCommand(UpdateProductRequest request);

    CreateVariantCommand toCommand(CreateVariantRequest request);

    UpdateVariantCommand toCommand(UpdateVariantRequest request);

    BarcodeInput toCommand(BarcodeInputRequest request);

    AddBarcodeCommand toCommand(AddBarcodeRequest request);

    UpdateBarcodeCommand toCommand(UpdateBarcodeRequest request);

    CreateCategoryCommand toCommand(CreateCategoryRequest request);

    UpdateCategoryCommand toCommand(UpdateCategoryRequest request);

    MoveCategoryCommand toCommand(MoveCategoryRequest request);

    CreateUnitCommand toCommand(CreateUnitRequest request);

    UpdateUnitCommand toCommand(UpdateUnitRequest request);

    ProductBarcodeResponse toResponse(ProductBarcodeDto dto);

    ProductVariantResponse toResponse(ProductVariantDto dto);

    List<ProductVariantResponse> toVariantResponses(List<ProductVariantDto> dtos);

    List<ProductBarcodeResponse> toBarcodeResponses(List<ProductBarcodeDto> dtos);

    ProductSummaryResponse toResponse(ProductSummaryDto dto);

    CategoryResponse toResponse(CategoryDto dto);

    List<CategoryResponse> toCategoryResponses(List<CategoryDto> dtos);

    CategoryTreeResponse toTreeResponse(CategoryTreeDto dto);

    List<CategoryTreeResponse> toCategoryTreeResponses(List<CategoryTreeDto> dtos);

    CategorySummaryResponse toSummaryResponse(CategoryDto dto);

    UnitOfMeasureResponse toResponse(UnitOfMeasureDto dto);

    List<UnitOfMeasureResponse> toUnitResponses(List<UnitOfMeasureDto> dtos);

    UnitSummaryResponse toSummaryResponse(UnitOfMeasureDto dto);

    VariantLookupResponse toResponse(VariantCatalogDto dto);

    @Mapping(target = "id", source = "detail.product.id")
    @Mapping(target = "name", source = "detail.product.name")
    @Mapping(target = "description", source = "detail.product.description")
    @Mapping(target = "brand", source = "detail.product.brand")
    @Mapping(target = "productType", source = "detail.product.productType")
    @Mapping(target = "variantOptions", source = "detail.product.variantOptions")
    @Mapping(target = "status", source = "detail.product.status")
    @Mapping(target = "category", source = "detail.category")
    @Mapping(target = "unit", source = "detail.unit")
    @Mapping(target = "variants", source = "detail.product.variants")
    @Mapping(target = "activeDefaultVariantId", expression = "java(resolveActiveDefaultId(detail.product()))")
    @Mapping(target = "historicalDefaultVariantId", expression = "java(resolveHistoricalDefaultId(detail.product()))")
    @Mapping(target = "createdAt", source = "detail.product.createdAt")
    @Mapping(target = "updatedAt", source = "detail.product.updatedAt")
    @Mapping(target = "version", source = "detail.product.version")
    ProductResponse toResponse(ProductDetailDto detail);

    default UUID resolveActiveDefaultId(ProductDto product) {
        return product.activeDefaultVariant().map(ProductVariantDto::id).orElse(null);
    }

    default UUID resolveHistoricalDefaultId(ProductDto product) {
        return product.historicalDefaultVariant().map(ProductVariantDto::id).orElse(null);
    }
}
