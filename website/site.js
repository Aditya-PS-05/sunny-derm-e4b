(() => {
  const header = document.querySelector('[data-header]');
  const menu = document.querySelector('[data-menu]');
  const toggle = document.querySelector('[data-menu-toggle]');

  const updateHeader = () => header?.classList.toggle('is-scrolled', window.scrollY > 12);
  updateHeader();
  window.addEventListener('scroll', updateHeader, { passive: true });

  const closeMenu = () => {
    if (!menu || !toggle) return;
    menu.classList.remove('is-open');
    toggle.classList.remove('is-open');
    toggle.setAttribute('aria-expanded', 'false');
    document.body.classList.remove('menu-open');
  };

  toggle?.addEventListener('click', () => {
    const willOpen = !menu?.classList.contains('is-open');
    menu?.classList.toggle('is-open', willOpen);
    toggle.classList.toggle('is-open', willOpen);
    toggle.setAttribute('aria-expanded', String(willOpen));
    document.body.classList.toggle('menu-open', willOpen);
  });

  menu?.querySelectorAll('a').forEach((link) => link.addEventListener('click', closeMenu));
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeMenu();
  });
  window.addEventListener('resize', () => {
    if (window.innerWidth > 760) closeMenu();
  });

  const waitlist = document.querySelector('[data-waitlist]');
  if (waitlist) {
    window.REQUIRED_CODE_ERROR_MESSAGE = 'Please choose a country code.';
    window.LOCALE = 'en';
    window.EMAIL_INVALID_MESSAGE = 'Enter a valid email address.';
    window.SMS_INVALID_MESSAGE = 'Enter a valid phone number.';
    window.REQUIRED_ERROR_MESSAGE = 'Enter your email address.';
    window.GENERIC_INVALID_MESSAGE = 'Check the information and try again.';
    window.INVALID_NUMBER = 'Enter a valid number.';
    window.INVALID_DATE = 'Enter a valid date.';
    window.REQUIRED_MULTISELECT_MESSAGE = 'Select at least one option.';
    window.translation = {
      common: {
        selectedList: '{quantity} list selected',
        selectedLists: '{quantity} lists selected',
        selectedOption: '{quantity} selected',
        selectedOptions: '{quantity} selected',
      },
    };
    window.AUTOHIDE = false;

    const success = waitlist.querySelector('#success-message');
    const error = waitlist.querySelector('#error-message');
    const entry = waitlist.querySelector('.form__entry');
    const email = waitlist.querySelector('#EMAIL');
    const syncWaitlistState = () => {
      const complete = success?.classList.contains('sib-form-message-panel--active') ?? false;
      waitlist.classList.toggle('is-complete', complete);
      email?.setAttribute('aria-invalid', String(entry?.classList.contains('entry_errored') ?? false));
    };
    const observer = new MutationObserver(syncWaitlistState);
    [success, error, entry].forEach((item) => {
      if (item) observer.observe(item, { attributes: true, attributeFilter: ['class'] });
    });
    syncWaitlistState();
  }

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const heroDither = document.querySelector('[data-hero-dither]');
  if (heroDither) {
    const gl = heroDither.getContext('webgl', {
      antialias: false,
      alpha: false,
      powerPreference: 'low-power',
    });

    if (gl) {
      const compileShader = (type, source) => {
        const shader = gl.createShader(type);
        gl.shaderSource(shader, source);
        gl.compileShader(shader);
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
          gl.deleteShader(shader);
          return null;
        }
        return shader;
      };

      const vertexShader = compileShader(gl.VERTEX_SHADER, `
        attribute vec2 position;
        void main() {
          gl_Position = vec4(position, 0.0, 1.0);
        }
      `);

      const fragmentShader = compileShader(gl.FRAGMENT_SHADER, `
        precision highp float;

        uniform vec2 resolution;
        uniform float time;
        uniform float reveal;

        float hash(vec2 point) {
          return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453123);
        }

        float noise(vec2 point) {
          vec2 integer = floor(point);
          vec2 fraction = fract(point);
          vec2 curve = fraction * fraction * (3.0 - 2.0 * fraction);
          return mix(
            mix(hash(integer), hash(integer + vec2(1.0, 0.0)), curve.x),
            mix(hash(integer + vec2(0.0, 1.0)), hash(integer + vec2(1.0, 1.0)), curve.x),
            curve.y
          );
        }

        float fbm(vec2 point) {
          float value = 0.0;
          float amplitude = 0.5;
          for (int octave = 0; octave < 5; octave++) {
            value += amplitude * noise(point);
            point *= 2.0;
            amplitude *= 0.5;
          }
          return value;
        }

        float bayer2(vec2 cell) {
          cell = floor(cell);
          return fract(cell.x / 2.0 + cell.y * cell.y * 0.75);
        }

        float bayer4(vec2 cell) {
          return bayer2(0.5 * cell) * 0.25 + bayer2(cell);
        }

        void main() {
          const float pixelSize = 5.0;
          vec2 cell = floor(gl_FragCoord.xy / pixelSize);
          vec2 uv = (cell * pixelSize + pixelSize * 0.5) / resolution;
          float aspectRatio = resolution.x / resolution.y;
          vec2 point = uv * vec2(aspectRatio, 1.0) * 1.25;
          float phase = time * 0.028;

          vec2 firstWarp = vec2(
            fbm(point + phase),
            fbm(point + vec2(5.2, 1.3) - phase)
          );
          vec2 secondWarp = vec2(
            fbm(point + 3.0 * firstWarp + vec2(1.7, 9.2) + 0.4 * phase),
            fbm(point + 3.0 * firstWarp + vec2(8.3, 2.8) - 0.4 * phase)
          );
          float field = fbm(point + 3.0 * secondWarp);
          field = smoothstep(-0.05, 1.05, field);

          float diagonal = (uv.x + (1.0 - uv.y)) * 0.5;
          diagonal += (fbm(point * 1.3 + 2.0) - 0.5) * 0.28;
          float firstBand = exp(-pow((diagonal - 0.40) / 0.115, 2.0));
          float secondBand = exp(-pow((diagonal - 0.58) / 0.125, 2.0));
          float streak = clamp(firstBand + secondBand, 0.0, 1.0);
          streak *= smoothstep(0.0, 0.22, uv.y);

          float value = pow(field, 0.45) * streak;
          float edgeWidth = 0.34;
          float edge = uv.x * 0.55 + (1.0 - uv.y) * 0.45;
          edge += (fbm(point * 1.6 + 4.0) - 0.5) * 0.22;
          value *= smoothstep(edge - edgeWidth, edge, reveal * (1.0 + edgeWidth));

          value = (value - 0.5) * 1.1 + 0.5;
          value = clamp(value, 0.0, 1.0);
          float threshold = bayer4(cell);
          float levels = 3.0;
          float quantised = floor(value * levels + (threshold - 0.5) + 0.5) / levels;
          quantised = clamp(quantised, 0.0, 1.0);

          vec3 paper = vec3(0.055, 0.055, 0.059);
          vec3 ink = vec3(0.910, 0.404, 0.310);
          gl_FragColor = vec4(mix(paper, ink, quantised), 1.0);
        }
      `);

      if (vertexShader && fragmentShader) {
        const program = gl.createProgram();
        gl.attachShader(program, vertexShader);
        gl.attachShader(program, fragmentShader);
        gl.linkProgram(program);

        if (gl.getProgramParameter(program, gl.LINK_STATUS)) {
          gl.useProgram(program);
          const buffer = gl.createBuffer();
          gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
          gl.bufferData(
            gl.ARRAY_BUFFER,
            new Float32Array([-1, -1, 3, -1, -1, 3]),
            gl.STATIC_DRAW,
          );
          const position = gl.getAttribLocation(program, 'position');
          gl.enableVertexAttribArray(position);
          gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);

          const resolution = gl.getUniformLocation(program, 'resolution');
          const time = gl.getUniformLocation(program, 'time');
          const reveal = gl.getUniformLocation(program, 'reveal');
          const startedAt = performance.now();
          let animationFrame = 0;
          let isVisible = true;

          const resizeCanvas = () => {
            const width = Math.max(1, Math.floor(heroDither.clientWidth));
            const height = Math.max(1, Math.floor(heroDither.clientHeight));
            if (heroDither.width === width && heroDither.height === height) return;
            heroDither.width = width;
            heroDither.height = height;
            gl.viewport(0, 0, width, height);
          };

          const drawDither = (now, staticFrame = false) => {
            resizeCanvas();
            const elapsed = staticFrame ? 8 : (now - startedAt) / 1000;
            const intro = staticFrame ? 1 : Math.min(1, elapsed / 4.2);
            gl.uniform2f(resolution, heroDither.width, heroDither.height);
            gl.uniform1f(time, elapsed);
            gl.uniform1f(reveal, 1 - ((1 - intro) ** 3));
            gl.drawArrays(gl.TRIANGLES, 0, 3);
          };

          const renderDither = (now) => {
            animationFrame = 0;
            if (!isVisible || document.hidden) return;
            drawDither(now);
            animationFrame = window.requestAnimationFrame(renderDither);
          };

          const requestRender = () => {
            if (reduceMotion) {
              drawDither(performance.now(), true);
              return;
            }
            if (isVisible && !document.hidden && !animationFrame) {
              animationFrame = window.requestAnimationFrame(renderDither);
            }
          };

          const visibilityObserver = new IntersectionObserver(([entry]) => {
            isVisible = entry.isIntersecting;
            if (!isVisible && animationFrame) {
              window.cancelAnimationFrame(animationFrame);
              animationFrame = 0;
            }
            requestRender();
          });
          visibilityObserver.observe(heroDither);

          if ('ResizeObserver' in window) {
            const resizeObserver = new ResizeObserver(requestRender);
            resizeObserver.observe(heroDither);
          } else {
            window.addEventListener('resize', requestRender);
          }
          document.addEventListener('visibilitychange', requestRender);
          requestRender();
        }
      }
    }
  }

  const phoneStory = document.querySelector('[data-phone-story]');
  if (phoneStory && !reduceMotion) {
    const phones = [...phoneStory.querySelectorAll('[data-phone]')];
    const captions = [...phoneStory.querySelectorAll('[data-phone-caption]')];
    const steps = [...phoneStory.querySelectorAll('[data-phone-step]')];
    let activeIndex = -1;
    let frame = 0;

    const clamp = (value, min, max) => Math.min(Math.max(value, min), max);
    const renderPhoneStory = () => {
      frame = 0;
      const rect = phoneStory.getBoundingClientRect();
      const travel = Math.max(phoneStory.offsetHeight - window.innerHeight, 1);
      const progress = clamp(-rect.top / travel, 0, 1);
      const nextIndex = Math.min(phones.length - 1, Math.floor(progress * phones.length));
      const spread = clamp(window.innerWidth * 0.34, 180, 410);

      phones.forEach((phone, index) => {
        const delta = index - nextIndex;
        const distance = Math.abs(delta);
        const limitedDelta = clamp(delta, -1.55, 1.55);
        const x = limitedDelta * spread;
        const y = Math.min(distance, 1.5) * 28;
        const z = -Math.min(distance, 1.75) * 210;
        const rotateY = limitedDelta * -38;
        const rotateX = -Math.min(distance, 1.5) * 2;
        const rotateZ = limitedDelta * 2.8;
        const scale = 1 - Math.min(distance, 1.6) * 0.055;
        phone.style.transform = `translate3d(calc(-50% + ${x}px), calc(-50% + ${y}px), ${z}px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) rotateZ(${rotateZ}deg) scale(${scale})`;
        phone.style.opacity = String(Math.max(0.08, 1 - distance * 0.62));
        phone.style.zIndex = String(100 - Math.round(distance * 10));
      });

      if (nextIndex === activeIndex) return;
      activeIndex = nextIndex;
      captions.forEach((caption, index) => {
        const isActive = index === activeIndex;
        caption.classList.toggle('is-active', isActive);
        caption.setAttribute('aria-hidden', String(!isActive));
      });
      steps.forEach((step, index) => step.classList.toggle('is-active', index === activeIndex));
    };

    const queuePhoneStory = () => {
      if (frame) return;
      frame = window.requestAnimationFrame(renderPhoneStory);
    };
    renderPhoneStory();
    window.addEventListener('scroll', queuePhoneStory, { passive: true });
    window.addEventListener('resize', queuePhoneStory);
  }

  const ledgerPreview = document.querySelector('[data-ledger-preview]');
  const previewRows = document.querySelectorAll('[data-feature-preview]');
  const canHover = window.matchMedia('(any-hover: hover) and (any-pointer: fine)').matches;
  if (ledgerPreview && previewRows.length && canHover) {
    const previewImage = ledgerPreview.querySelector('[data-ledger-preview-image]');
    const clampPosition = (value, min, max) => Math.min(Math.max(value, min), max);
    let pointerX = 0;
    let pointerY = 0;
    let previewFrame = 0;

    const positionPreview = () => {
      previewFrame = 0;
      const previewWidth = ledgerPreview.offsetWidth || 216;
      const previewHeight = ledgerPreview.offsetHeight || 450;
      let x = pointerX - previewWidth / 2;
      let y = pointerY - previewHeight / 2;
      x = clampPosition(x, 20, window.innerWidth - previewWidth - 20);
      y = clampPosition(y, 92, Math.max(92, window.innerHeight - previewHeight - 20));
      ledgerPreview.style.transform = `translate3d(${Math.round(x)}px, ${Math.round(y)}px, 0)`;
    };
    const trackPreview = (event) => {
      pointerX = event.clientX;
      pointerY = event.clientY;
      if (!previewFrame) previewFrame = window.requestAnimationFrame(positionPreview);
    };

    previewRows.forEach((row) => {
      const source = row.dataset.featurePreview;
      if (source) {
        const preload = new Image();
        preload.src = source;
      }

      row.addEventListener('pointerenter', (event) => {
        if (previewImage && source) previewImage.src = source;
        trackPreview(event);
        window.requestAnimationFrame(() => ledgerPreview.classList.add('is-visible'));
      });
      row.addEventListener('pointermove', trackPreview, { passive: true });
      row.addEventListener('pointerleave', () => ledgerPreview.classList.remove('is-visible'));
    });
    window.addEventListener('scroll', () => ledgerPreview.classList.remove('is-visible'), { passive: true });
  }

  const reveals = document.querySelectorAll('[data-reveal]');
  if (reduceMotion || !('IntersectionObserver' in window)) {
    reveals.forEach((item) => item.classList.add('is-visible'));
    return;
  }

  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('is-visible');
      observer.unobserve(entry.target);
    });
  }, { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });
  reveals.forEach((item) => observer.observe(item));
})();
