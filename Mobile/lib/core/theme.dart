import 'package:flutter/material.dart';

/// Token lấy từ Web/tailwind.config.js (Figma).
abstract final class AppColors {
  static const page = Color(0xFFFFF8F1);
  static const surface = Color(0xFFFFFFFF);
  static const subtle = Color(0xFFFFF1E6);
  static const input = Color(0xFFFFFCF9);
  static const brand = Color(0xFFE8590C);
  static const brandStrong = Color(0xFFC2410C);
  static const ink = Color(0xFF1F2433);
  static const inkSecondary = Color(0xFF5B6170);
  static const lineInput = Color(0xFFF3D9C4);
  static const danger = Color(0xFFDC2626);
  static const dangerFg = Color(0xFFA61C26);
}

const double radiusMd = 16;

// Phông Nunito chưa được đóng gói: máy dùng phông hệ thống cho tới khi thêm asset (xem README).
ThemeData buildTheme() {
  OutlineInputBorder border(Color c, [double w = 1.5]) => OutlineInputBorder(
        borderRadius: BorderRadius.circular(radiusMd),
        borderSide: BorderSide(color: c, width: w),
      );
  return ThemeData(
    useMaterial3: true,
    fontFamily: 'Nunito',
    colorScheme: ColorScheme.fromSeed(
      seedColor: AppColors.brand,
      primary: AppColors.brand,
      error: AppColors.danger,
    ),
    scaffoldBackgroundColor: AppColors.page,
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.page,
      foregroundColor: AppColors.ink,
      elevation: 0,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: AppColors.input,
      border: border(AppColors.lineInput),
      enabledBorder: border(AppColors.lineInput),
      focusedBorder: border(AppColors.brand, 2),
      errorBorder: border(AppColors.danger),
      focusedErrorBorder: border(AppColors.danger, 2),
      errorStyle: const TextStyle(color: AppColors.dangerFg),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.brand,
        foregroundColor: Colors.white,
        minimumSize: const Size(48, 52),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(radiusMd),
        ),
        textStyle: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: AppColors.brandStrong,
        minimumSize: const Size(48, 48),
        textStyle: const TextStyle(fontWeight: FontWeight.w800),
      ),
    ),
    navigationBarTheme: const NavigationBarThemeData(
      backgroundColor: AppColors.surface,
      indicatorColor: AppColors.subtle,
    ),
  );
}
