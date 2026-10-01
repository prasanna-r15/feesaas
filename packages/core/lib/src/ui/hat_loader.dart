import 'dart:math' as math;

import 'package:flutter/material.dart';

class HatOrbitLoader extends StatefulWidget {
  const HatOrbitLoader({
    super.key,
    required this.personal,
    this.flip = false,
    this.size = 168,
  });

  final bool personal;
  final bool flip;
  final double size;

  @override
  State<HatOrbitLoader> createState() => _HatOrbitLoaderState();
}

class _HatOrbitLoaderState extends State<HatOrbitLoader> with TickerProviderStateMixin {
  late final AnimationController _spin;
  late final AnimationController _enter;

  @override
  void initState() {
    super.initState();
    _spin = AnimationController(vsync: this, duration: const Duration(milliseconds: 2400))..repeat();
    _enter = AnimationController(vsync: this, duration: const Duration(milliseconds: 900))..forward();
  }

  @override
  void dispose() {
    _spin.dispose();
    _enter.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final toIcon = widget.personal ? Icons.savings_rounded : Icons.storefront_rounded;
    final fromIcon = widget.personal ? Icons.storefront_rounded : Icons.savings_rounded;
    final accent = widget.personal ? const Color(0xFFFBBF24) : const Color(0xFF5EEAD4);
    final size = widget.size;
    return AnimatedBuilder(
      animation: Listenable.merge([_spin, _enter]),
      builder: (context, _) {
        final enter = Curves.easeOutCubic.transform(_enter.value);
        return Opacity(
          opacity: 0.55 + (0.45 * enter),
          child: Transform.scale(
            scale: 0.88 + (0.12 * enter),
            child: SizedBox(
              width: size,
              height: size,
              child: Stack(
                alignment: Alignment.center,
                children: [
                  CustomPaint(
                    size: Size.square(size),
                    painter: _OrbitPainter(turn: _spin.value, accent: accent),
                  ),
                  Container(
                    width: size * 0.54,
                    height: size * 0.54,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: RadialGradient(
                        colors: [
                          accent.withValues(alpha: 0.28),
                          const Color(0xFF134E4A).withValues(alpha: 0.12),
                        ],
                      ),
                    ),
                  ),
                  SizedBox(
                    width: size * 0.32,
                    height: size * 0.32,
                    child: Center(
                      child: widget.flip
                          ? _FlipGlyph(progress: enter, from: fromIcon, to: toIcon, color: accent, size: size * 0.28)
                          : Icon(toIcon, size: size * 0.28, color: accent),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _FlipGlyph extends StatelessWidget {
  const _FlipGlyph({
    required this.progress,
    required this.from,
    required this.to,
    required this.color,
    required this.size,
  });

  final double progress;
  final IconData from;
  final IconData to;
  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) {
    final t = ((progress - 0.12) / 0.7).clamp(0.0, 1.0);
    final showTo = t >= 0.5;
    final angle = showTo ? (1 - t) * math.pi : t * math.pi;
    return Transform(
      alignment: Alignment.center,
      transform: Matrix4.identity()
        ..setEntry(3, 2, 0.001)
        ..rotateY(angle),
      child: Icon(showTo ? to : from, size: size, color: color),
    );
  }
}

class _OrbitPainter extends CustomPainter {
  _OrbitPainter({required this.turn, required this.accent});

  final double turn;
  final Color accent;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = (math.min(size.width, size.height) / 2) - 10;
    final ring = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.4
      ..strokeCap = StrokeCap.round
      ..color = accent.withValues(alpha: 0.7);
    final track = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1
      ..color = Colors.white.withValues(alpha: 0.14);

    canvas.drawCircle(center, radius, track);
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      turn * math.pi * 2,
      math.pi * 1.15,
      false,
      ring,
    );
    final inner = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.6
      ..strokeCap = StrokeCap.round
      ..color = Colors.white.withValues(alpha: 0.28);
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius - 14),
      -turn * math.pi * 2.4,
      math.pi * 0.55,
      false,
      inner,
    );

    final sparkAngle = turn * math.pi * 2;
    final spark = Offset(
      center.dx + math.cos(sparkAngle) * radius,
      center.dy + math.sin(sparkAngle) * radius,
    );
    canvas.drawCircle(spark, 5, Paint()..color = accent);
    canvas.drawCircle(spark, 11, Paint()..color = accent.withValues(alpha: 0.22));
  }

  @override
  bool shouldRepaint(covariant _OrbitPainter oldDelegate) {
    return oldDelegate.turn != turn || oldDelegate.accent != accent;
  }
}
