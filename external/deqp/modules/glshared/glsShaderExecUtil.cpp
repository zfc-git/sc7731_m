/*-------------------------------------------------------------------------
 * drawElements Quality Program OpenGL (ES) Module
 * -----------------------------------------------
 *
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *//*!
 * \file
 * \brief Shader execution utilities.
 *//*--------------------------------------------------------------------*/

#include "glsShaderExecUtil.hpp"
#include "gluRenderContext.hpp"
#include "gluDrawUtil.hpp"
#include "gluObjectWrapper.hpp"
#include "gluShaderProgram.hpp"
#include "gluTextureUtil.hpp"
#include "gluProgramInterfaceQuery.hpp"
#include "gluPixelTransfer.hpp"
#include "gluStrUtil.hpp"
#include "tcuTestLog.hpp"
#include "glwFunctions.hpp"
#include "glwEnums.hpp"
#include "deSTLUtil.hpp"
#include "deStringUtil.hpp"
#include "deUniquePtr.hpp"
#include "deMemory.h"

#include <map>

namespace deqp
{
namespace gls
{

namespace ShaderExecUtil
{

using std::vector;

static bool isExtensionSupported (const glu::RenderContext& renderCtx, const std::string& extension)
{
	const glw::Functions&	gl		= renderCtx.getFunctions();
	int						numExts	= 0;

	gl.getIntegerv(GL_NUM_EXTENSIONS, &numExts);

	for (int ndx = 0; ndx < numExts; ndx++)
	{
		const char* curExt = (const char*)gl.getStringi(GL_EXTENSIONS, ndx);

		if (extension == curExt)
			return true;
	}

	return false;
}

static void checkExtension (const glu::RenderContext& renderCtx, const std::string& extension)
{
	if (!isExtensionSupported(renderCtx, extension))
		throw tcu::NotSupportedError(extension + " is not supported");
}

static void checkLimit (const glu::RenderContext& renderCtx, deUint32 pname, int required)
{
	const glw::Functions&	gl					= renderCtx.getFunctions();
	int						implementationLimit	= -1;
	deUint32				error;

	gl.getIntegerv(pname, &implementationLimit);
	error = gl.getError();

	if (error != GL_NO_ERROR)
		throw tcu::TestError("Failed to query " + de::toString(glu::getGettableStateStr(pname)) + " - got " + de::toString(glu::getErrorStr(error)));
	if (implementationLimit < required)
		throw tcu::NotSupportedError("Test requires " + de::toString(glu::getGettableStateStr(pname)) + " >= " + de::toString(required) + ", got " + de::toString(implementationLimit));
}

// Shader utilities

static std::string generateVertexShader (const ShaderSpec& shaderSpec)
{
	const bool			usesInout	= glu::glslVersionUsesInOutQualifiers(shaderSpec.version);
	const char*			in			= usesInout ? "in"		: "attribute";
	const char*			out			= usesInout ? "out"		: "varying";
	std::ostringstream	src;

	src << glu::getGLSLVersionDeclaration(shaderSpec.version) << "\n";

	if (!shaderSpec.globalDeclarations.empty())
		src << shaderSpec.globalDeclarations << "\n";

	for (vector<Symbol>::const_iterator input = shaderSpec.inputs.begin(); input != shaderSpec.inputs.end(); ++input)
		src << in << " " << glu::declare(input->varType, input->name) << ";\n";

	for (vector<Symbol>::const_iterator output = shaderSpec.outputs.begin(); output != shaderSpec.outputs.end(); ++output)
	{
		DE_ASSERT(output->varType.isBasicType());

		if (glu::isDataTypeBoolOrBVec(output->varType.getBasicType()))
		{
			const int				vecSize		= glu::getDataTypeScalarSize(output->varType.getBasicType());
			const glu::DataType		intBaseType	= vecSize > 1 ? glu::getDataTypeIntVec(vecSize) : glu::TYPE_INT;
			const glu::VarType		intType		(intBaseType, glu::PRECISION_HIGHP);

			src << "flat " << out << " " << glu::declare(intType, "o_" + output->name) << ";\n";
		}
		else
			src << "flat " << out << " " << glu::declare(output->varType, output->name) << ";\n";
	}

	src << "\n"
		<< "void main (void)\n"
		<< "{\n"
		<< "	gl_Position = vec4(0.0);\n"
		<< "	gl_PointSize = 1.0;\n\n";

	// Declare necessary output variables (bools).
	for (vector<Symbol>::const_iterator output = shaderSpec.outputs.begin(); output != shaderSpec.outputs.end(); ++output)
	{
		if (glu::isDataTypeBoolOrBVec(output->varType.getBasicType()))
			src << "\t" << glu::declare(output->varType, output->name) << ";\n";
	}

	// Operation - indented to correct level.
	{
		std::istringstream	opSrc	(shaderSpec.source);
		std::string			line;

		while (std::getline(opSrc, line))
			src << "\t" << line << "\n";
	}

	// Assignments to outputs.
	for (vector<Symbol>::const_iterator output = shaderSpec.outputs.begin(); output != shaderSpec.outputs.end(); ++output)
	{
		if (glu::isDataTypeBoolOrBVec(output->varType.getBasicType()))
		{
			const int				vecSize		= glu::getDataTypeScalarSize(output->varType.getBasicType());
			const glu::DataType		intBaseType	= vecSize > 1 ? glu::getDataTypeIntVec(vecSize) : glu::TYPE_INT;

			src << "\to_" << output->name << " = " << glu::getDataTypeName(intBaseType) << "(" << output->name << ");\n";
		}
	}

	src << "}\n";

	return src.str();
}

static std::string generateGeometryShader (const ShaderSpec& shaderSpec)
{
	DE_ASSERT(glu::glslVersionUsesInOutQualifiers(shaderSpec.version));

	std::ostringstream	src;

	src << glu::getGLSLVersionDeclaration(shaderSpec.version) << "\n";

	if (glu::glslVersionIsES(shaderSpec.version) && shaderSpec.version <= glu::GLSL_VERSION_310_ES)
		src << "#extension GL_EXT_geometry_shader : require\n";

	if (!shaderSpec.globalDeclarations.empty())
		src << shaderSpec.globalDeclarations << "\n";

	src << "layout(points) in;\n"
		<< "layout(points, max_vertices = 1) out;\n";

	for (vector<Symbol>::const_iterator input = shaderSpec.inputs.begin(); input != shaderSpec.inputs.end(); ++input)
		src << "flat in " << glu::declare(input->varType, "geom_" + input->name) << "[];\n";

	for (vector<Symbol>::const_iterator output = shaderSpec.outputs.begin(); output != shaderSpec.outputs.end(); ++output)
	{
		DE_ASSERT(output->varType.isBasicType());

		if (glu::isDataTypeBoolOrBVec(output->varType.getBasicType()))
		{
			const int				vecSize		= glu::getDataTypeScalarSize(output->varType.getBasicType());
			const glu::DataType		intBaseType	= vecSize > 1 ? glu::getDataTypeIntVec(vecSize) : glu::TYPE_INT;
			const glu::VarType		intType		(intBaseType, glu::PRECISION_HIGHP);

			src << "flat out " << glu::declare(intType, "o_" + output->name) << ";\n";
		}
		else
			src << "flat out " << glu::declare(output->varType, output->name) << ";\n";
	}

	src << "\n"
		<< "void main (void)\n"
		<< "{\n"
		<< "	gl_Position = gl_in[0].gl_Position;\n\n";

	// Fetch input variables
	for (vector<Symbol>::const_iterator input = shaderSpec.inputs.begin(); input != shaderSpec.inputs.end(); ++input)
		src << "\t" << glu::declare(input->varType, input->name) << " = geom_" << input->name << "[0];\n";

	// Declare necessary output variables (bools).
	for (vector<Symbol>::const_iterator output = shaderSpec.outputs.begin(); output != shaderSpec.outputs.end(); ++output)
	{
		if (glu::isDataTypeBoolOrBVec(output->varType.getBasicType()))
			src << "\t" << glu::declare(output->varType, output->name) << ";\n";
	}

	src << "\n";

	// Operation - indented to correct level.
	{
		std::istringstream	opSrc	(shaderSpec.source);
		std::string			line;

		while (std::getline(opSrc, line))
			src << "\t" << line << "\n";
	}

	// Assignments to outputs.
	for (vector<Symbol>::const_iterator output = shaderSpec.outputs.begin(); output != shaderSpec.outputs.end(); ++output)
	{
		if (glu::isDataTypeBoolOrBVec(output->varType.getBasicType()))
		{
			const int				vecSize		= glu::getDataTypeScalarSize(output->varType.getBasicType());
			const glu::DataType		intBaseType	= vecSize > 1 ? glu::getDataTypeIntVec(vecSize) : glu::TYPE_INT;

			src << "\to_" << output->name << " = " << glu::getDataTypeName(intBaseType) << "(" << output->name << ");\n";
		}
	}

	src << "	EmitVertex();\n"
		<< "	EndPrimitive();\n"
		<< "}\n";

	return src.str();
}

static std::string generateEmptyFragmentSource (glu::GLSLVersion version)
{
	const bool			customOut		= glu::glslVersionUsesInOutQualifiers(version);
	std::ostringstream	src;

	src << glu::getGLSLVersionDeclaration(version) << "\n";

	// \todo [2013-08-05 pyry] Do we need one dummy output?

	src << "void main (void)\n{\n";
	if (!customOut)
		src << "	gl_FragColor = vec4(0.0);\n";
	src << "}\n";

	return src.str();
}

static std::string generatePassthroughVertexShader (const ShaderSpec& shaderSpec, const char* inputPrefix, const char* outputPrefix)
{
	// flat qualifier is not present in earlier versions?
	DE_ASSERT(glu::glslVersionUsesInOutQualifiers(shaderSpec.version));

	std::ostringstream src;

	src << glu::getGLSLVersionDeclaration(shaderSpec.version) << "\n"
		<< "in highp vec4 a_position;\n";

	for (vector<Symbol>::const_iterator input = shaderSpec.inputs.begin(); input != shaderSpec.inputs.end(); ++input)
	{
		src << "in " << glu::declare(input->varType, inputPrefix + input->name) << ";\n"
			<< "flat out " << glu::declare(input->varType, outputPrefix + input->name) << ";\n";
	}

	src << "\nvoid main (void)\n{\n"
		<< "	gl_Position = a_position;\n"
		<< "	gl_PointSize = 1.0;\n";

	for (vector<Symbol>::const_iterator input = shaderSpec.inputs.begin(); input != shaderSpec.inputs.end(); ++input)
		src << "\t" << outputPrefix << input->name << " = " << inputPrefix << input->name << ";\n";

	src << "}\n";

	return src.str();
}

static std::string generateFragmentShader (const ShaderSpec& shaderSpec, bool useIntOutputs, const std::map<std::string, int>& outLocationMap)
{
	DE_ASSERT(glu::glslVersionUsesInOutQualifiers(shaderSpec.version));

	std::ostringstream	src;
	src << glu::getGLSLVersionDeclaration(shaderSpec.version) << "\n";

	if (!shaderSpec.globalDeclarations.empty())
		src << shaderSpec.globalDeclarations << "\n";

	for (vector<Symbol>::const_iterator input = shaderSpec.inputs.begin(); input != shaderSpec.inputs.end(); ++input)
		src << "flat in " << glu::declare(input->varType, input->name) << ";\n";

	for (int outNdx = 0; outNdx < (int)shaderSpec.outputs.size(); ++outNdx)
	{
		const Symbol&				output		= shaderSpec.outputs[outNdx];
		const int					location	= de::lookup(outLocationMap, output.name);
		const std::string			outVarName	= "o_" + output.name;
		glu::VariableDeclaration	decl		(output.varType, outVarName, glu::STORAGE_OUT, glu::INTERPOLATION_LAST, glu::Layout(location));

		TCU_CHECK_INTERNAL(output.varType.isBasicType());

		if (useIntOutputs && glu::isDataTypeFloatOrVec(output.varType.getBasicType()))
		{
			const int			vecSize			= glu::getDataTypeScalarSize(output.varType.getBasicType());
			const glu::DataType	uintBasicType	= vecSize > 1 ? glu::getDataTypeUintVec(vecSize) : glu::TYPE_UINT;
			const glu::VarType	uintType		(uintBasicType, glu::PRECISION_HIGHP);

			decl.varType = uintType;
			src << decl << ";\n";
		}
		else if (glu::isDataTypeBoolOrBVec(output.varType.getBasicType()))
		{
			const int			vecSize			= glu::getDataTypeScalarSize(output.varType.getBasicType());
			const glu::DataType	intBasicType	= vecSize > 1 ? glu::getDataTypeIntVec(vecSize) : glu::TYPE_INT;
			const glu::VarType	intType			(intBasicType, glu::PRECISION_HIGHP);

			decl.varType = intType;
			src << decl << ";\n";
		}
		else if (glu::isDataTypeMatrix(output.varType.getBasicType()))
		{
			const int			vecSize			= glu::getDataTypeMatrixNumRows(output.varType.getBasicType());
			const int			numVecs			= glu::getDataTypeMatrixNumColumns(output.varType.getBasicType());
			const glu::DataType	uintBasicType	= glu::getDataTypeUintVec(vecSize);
			const glu::VarType	uintType		(uintBasicType, glu::PRECISION_HIGHP);

			decl.varType = uintType;
			for (int vecNdx = 0; vecNdx < numVecs; ++vecNdx)
			{
				decl.name				= outVarName + "_" + de::toString(vecNdx);
				decl.layout.location	= location + vecNdx;
				src << decl << ";\n";
			}
		}
		else
			src << glu::VariableDeclaration(output.varType, output.name, glu::STORAGE_OUT, glu::INTERPOLATION_LAST, location) << ";\n";
	}

	src << "\nvoid main (void)\n{\n";

	for (vector<Symbol>::const_iterator output = shaderSpec.outputs.begin(); output != shaderSpec.outputs.end(); ++output)
	{
		if ((useIntOutputs && glu::isDataTypeFloatOrVec(output->varType.getBasicType())) ||
			glu::isDataTypeBoolOrBVec(output->varType.getBasicType()) ||
			glu::isDataTypeMatrix(output->varType.getBasicType()))
			src << "\t" << glu::declare(output->varType, output->name) << ";\n";
	}

	// Operation - indented to correct level.
	{
		std::istringstream	opSrc	(shaderSpec.source);
		std::string			line;

		while (std::getline(opSrc, line))
			src << "\t" << line << "\n";
	}

	for (vector<Symbol>::const_iterator output = shaderSpec.outputs.begin(); output != shaderSpec.outputs.end(); ++output)
	{
		if (useIntOutputs && glu::isDataTypeFloatOrVec(output->varType.getBasicType()))
			src << "	o_" << output->name << " = floatBitsToUint(" << output->name << ");\n";
		else if (glu::isDataTypeMatrix(output->varType.getBasicType()))
		{
			const int			numVecs			= glu::getDataTypeMatrixNumColumns(output->varType.getBasicType());

			for (int vecNdx = 0; vecNdx < numVecs; ++vecNdx)
				if (useIntOutputs)
					src << "\to_" << output->name << "_" << vecNdx << " = floatBitsToUint(" << output->name << "[" << vecNdx << "]);\n";
				else
					src << "\to_" << output->name << "_" << vecNdx << " = " << output->name << "[" << vecNdx << "];\n";
		}
		else if (glu::isDataTypeBoolOrBVec(output->varType.getBasicType()))
		{
			const int				vecSize		= glu::getDataTypeScalarSize(output->varType.getBasicType());
			const glu::DataType		intBaseType	= vecSize > 1 ? glu::getDataTypeIntVec(vecSize) : glu::TYPE_INT;

			src << "\to_" << output->name << " = " << glu::getDataTypeName(intBaseType) << "(" << output->name << ");\n";
		}
	}

	src << "}\n";

	return src.str();
}

// ShaderExecutor

ShaderExecutor::ShaderExecutor (const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec)
	: m_renderCtx	(renderCtx)
	, m_inputs		(shaderSpec.inputs)
	, m_outputs		(shaderSpec.outputs)
{
}

ShaderExecutor::~ShaderExecutor (void)
{
}

void ShaderExecutor::useProgram (void)
{
	DE_ASSERT(isOk());
	m_renderCtx.getFunctions().useProgram(getProgram());
}

// VertexProcessorExecutor (base class for vertex and geometry executors)

class VertexProcessorExecutor : public ShaderExecutor
{
public:
								VertexProcessorExecutor	(const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec, const glu::ProgramSources& sources);
								~VertexProcessorExecutor(void);

	bool						isOk					(void) const				{ return m_program.isOk();			}
	void						log						(tcu::TestLog& dst) const	{ dst << m_program;					}
	deUint32					getProgram				(void) const				{ return m_program.getProgram();	}

	void						execute					(int numValues, const void* const* inputs, void* const* outputs);

protected:
	glu::ShaderProgram			m_program;
};

template<typename Iterator>
struct SymbolNameIterator
{
	Iterator symbolIter;

	SymbolNameIterator (Iterator symbolIter_) : symbolIter(symbolIter_) {}

	inline SymbolNameIterator&	operator++	(void)								{ ++symbolIter; return *this;				}

	inline bool					operator==	(const SymbolNameIterator& other)	{ return symbolIter == other.symbolIter;	}
	inline bool					operator!=	(const SymbolNameIterator& other)	{ return symbolIter != other.symbolIter;	}

	inline std::string operator* (void) const
	{
		if (glu::isDataTypeBoolOrBVec(symbolIter->varType.getBasicType()))
			return "o_" + symbolIter->name;
		else
			return symbolIter->name;
	}
};

template<typename Iterator>
inline glu::TransformFeedbackVaryings<SymbolNameIterator<Iterator> > getTFVaryings (Iterator begin, Iterator end)
{
	return glu::TransformFeedbackVaryings<SymbolNameIterator<Iterator> >(SymbolNameIterator<Iterator>(begin), SymbolNameIterator<Iterator>(end));
}

VertexProcessorExecutor::VertexProcessorExecutor (const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec, const glu::ProgramSources& sources)
	: ShaderExecutor	(renderCtx, shaderSpec)
	, m_program			(renderCtx,
						 glu::ProgramSources(sources) << getTFVaryings(shaderSpec.outputs.begin(), shaderSpec.outputs.end())
													  << glu::TransformFeedbackMode(GL_INTERLEAVED_ATTRIBS))
{
}

VertexProcessorExecutor::~VertexProcessorExecutor (void)
{
}

template<typename Iterator>
static int computeTotalScalarSize (Iterator begin, Iterator end)
{
	int size = 0;
	for (Iterator cur = begin; cur != end; ++cur)
		size += cur->varType.getScalarSize();
	return size;
}

void VertexProcessorExecutor::execute (int numValues, const void* const* inputs, void* const* outputs)
{
	const glw::Functions&					gl					= m_renderCtx.getFunctions();
	const bool								useTFObject			= isContextTypeES(m_renderCtx.getType()) || (isContextTypeGLCore(m_renderCtx.getType()) && m_renderCtx.getType().getMajorVersion() >= 4);
	vector<glu::VertexArrayBinding>			vertexArrays;
	de::UniquePtr<glu::TransformFeedback>	transformFeedback	(useTFObject ? new glu::TransformFeedback(m_renderCtx) : DE_NULL);
	glu::Buffer								outputBuffer		(m_renderCtx);
	const int								outputBufferStride	= computeTotalScalarSize(m_outputs.begin(), m_outputs.end())*sizeof(deUint32);

	// Setup inputs.
	for (int inputNdx = 0; inputNdx < (int)m_inputs.size(); inputNdx++)
	{
		const Symbol&		symbol		= m_inputs[inputNdx];
		const void*			ptr			= inputs[inputNdx];
		const glu::DataType	basicType	= symbol.varType.getBasicType();
		const int			vecSize		= glu::getDataTypeScalarSize(basicType);

		if (glu::isDataTypeFloatOrVec(basicType))
			vertexArrays.push_back(glu::va::Float(symbol.name, vecSize, numValues, 0, (const float*)ptr));
		else if (glu::isDataTypeIntOrIVec(basicType))
			vertexArrays.push_back(glu::va::Int32(symbol.name, vecSize, numValues, 0, (const deInt32*)ptr));
		else if (glu::isDataTypeUintOrUVec(basicType))
			vertexArrays.push_back(glu::va::Uint32(symbol.name, vecSize, numValues, 0, (const deUint32*)ptr));
		else if (glu::isDataTypeMatrix(basicType))
		{
			int		numRows	= glu::getDataTypeMatrixNumRows(basicType);
			int		numCols	= glu::getDataTypeMatrixNumColumns(basicType);
			int		stride	= numRows * numCols * sizeof(float);

			for (int colNdx = 0; colNdx < numCols; ++colNdx)
				vertexArrays.push_back(glu::va::Float(symbol.name, colNdx, numRows, numValues, stride, ((const float*)ptr) + colNdx * numRows));
		}
		else
			DE_ASSERT(false);
	}

	// Setup TF outputs.
	if (useTFObject)
		gl.bindTransformFeedback(GL_TRANSFORM_FEEDBACK, **transformFeedback);
	gl.bindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, *outputBuffer);
	gl.bufferData(GL_TRANSFORM_FEEDBACK_BUFFER, outputBufferStride*numValues, DE_NULL, GL_STREAM_READ);
	gl.bindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, *outputBuffer);
	GLU_EXPECT_NO_ERROR(gl.getError(), "Error in TF setup");

	// Draw with rasterization disabled.
	gl.beginTransformFeedback(GL_POINTS);
	gl.enable(GL_RASTERIZER_DISCARD);
	glu::draw(m_renderCtx, m_program.getProgram(), (int)vertexArrays.size(), vertexArrays.empty() ? DE_NULL : &vertexArrays[0],
			  glu::pr::Points(numValues));
	gl.disable(GL_RASTERIZER_DISCARD);
	gl.endTransformFeedback();
	GLU_EXPECT_NO_ERROR(gl.getError(), "Error in draw");

	// Read back data.
	{
		const void*	srcPtr		= gl.mapBufferRange(GL_TRANSFORM_FEEDBACK_BUFFER, 0, outputBufferStride*numValues, GL_MAP_READ_BIT);
		int			curOffset	= 0; // Offset in buffer in bytes.

		GLU_EXPECT_NO_ERROR(gl.getError(), "glMapBufferRange(GL_TRANSFORM_FEEDBACK_BUFFER)");
		TCU_CHECK(srcPtr != DE_NULL);

		for (int outputNdx = 0; outputNdx < (int)m_outputs.size(); outputNdx++)
		{
			const Symbol&		symbol		= m_outputs[outputNdx];
			void*				dstPtr		= outputs[outputNdx];
			const int			scalarSize	= symbol.varType.getScalarSize();

			for (int ndx = 0; ndx < numValues; ndx++)
				deMemcpy((deUint32*)dstPtr + scalarSize*ndx, (const deUint8*)srcPtr + curOffset + ndx*outputBufferStride, scalarSize*sizeof(deUint32));

			curOffset += scalarSize*sizeof(deUint32);
		}

		gl.unmapBuffer(GL_TRANSFORM_FEEDBACK_BUFFER);
		GLU_EXPECT_NO_ERROR(gl.getError(), "glUnmapBuffer()");
	}

	if (useTFObject)
		gl.bindTransformFeedback(GL_TRANSFORM_FEEDBACK, 0);
	gl.bindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, 0);
	GLU_EXPECT_NO_ERROR(gl.getError(), "Restore state");
}

// VertexShaderExecutor

class VertexShaderExecutor : public VertexProcessorExecutor
{
public:
								VertexShaderExecutor	(const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec);
};

VertexShaderExecutor::VertexShaderExecutor (const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec)
	: VertexProcessorExecutor	(renderCtx, shaderSpec,
								 glu::ProgramSources() << glu::VertexSource(generateVertexShader(shaderSpec))
													   << glu::FragmentSource(generateEmptyFragmentSource(shaderSpec.version)))
{
}

// GeometryShaderExecutor

class CheckGeomSupport
{
public:
	inline CheckGeomSupport (const glu::RenderContext& renderCtx)
	{
		if (renderCtx.getType().getAPI().getProfile() == glu::PROFILE_ES)
			checkExtension(renderCtx, "GL_EXT_geometry_shader");
	}
};

class GeometryShaderExecutor : private CheckGeomSupport, public VertexProcessorExecutor
{
public:
								GeometryShaderExecutor	(const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec);
};

GeometryShaderExecutor::GeometryShaderExecutor (const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec)
	: CheckGeomSupport			(renderCtx)
	, VertexProcessorExecutor	(renderCtx, shaderSpec,
								 glu::ProgramSources() << glu::VertexSource(generatePassthroughVertexShader(shaderSpec, "", "geom_"))
													   << glu::GeometrySource(generateGeometryShader(shaderSpec))
													   << glu::FragmentSource(generateEmptyFragmentSource(shaderSpec.version)))
{
}

// FragmentShaderExecutor

class FragmentShaderExecutor : public ShaderExecutor
{
public:
								FragmentShaderExecutor	(const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec);
								~FragmentShaderExecutor	(void);

	bool						isOk					(void) const				{ return m_program.isOk();			}
	void						log						(tcu::TestLog& dst) const	{ dst << m_program;					}
	deUint32					getProgram				(void) const				{ return m_program.getProgram();	}

	void						execute					(int numValues, const void* const* inputs, void* const* outputs);

protected:
	std::vector<const Symbol*>	m_outLocationSymbols;
	std::map<std::string, int>	m_outLocationMap;
	glu::ShaderProgram			m_program;
};

static std::map<std::string, int> generateLocationMap (const std::vector<Symbol>& symbols, std::vector<const Symbol*>& locationSymbols)
{
	std::map<std::string, int>	ret;
	int							location	= 0;

	locationSymbols.clear();

	for (std::vector<Symbol>::const_iterator it = symbols.begin(); it != symbols.end(); ++it)
	{
		const int	numLocations	= glu::getDataTypeNumLocations(it->varType.getBasicType());

		TCU_CHECK_INTERNAL(!de::contains(ret, it->name));
		de::insert(ret, it->name, location);
		location += numLocations;

		for (int ndx = 0; ndx < numLocations; ++ndx)
			locationSymbols.push_back(&*it);
	}

	return ret;
}

inline bool hasFloatRenderTargets (const glu::RenderContext& renderCtx)
{
	glu::ContextType type = renderCtx.getType();
	return glu::isContextTypeGLCore(type);
}

FragmentShaderExecutor::FragmentShaderExecutor (const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec)
	: ShaderExecutor		(renderCtx, shaderSpec)
	, m_outLocationSymbols	()
	, m_outLocationMap		(generateLocationMap(m_outputs, m_outLocationSymbols))
	, m_program				(renderCtx,
							 glu::ProgramSources() << glu::VertexSource(generatePassthroughVertexShader(shaderSpec, "a_", ""))
												   << glu::FragmentSource(generateFragmentShader(shaderSpec, !hasFloatRenderTargets(renderCtx), m_outLocationMap)))
{
}

FragmentShaderExecutor::~FragmentShaderExecutor (void)
{
}

inline int queryInt (const glw::Functions& gl, deUint32 pname)
{
	int value = 0;
	gl.getIntegerv(pname, &value);
	return value;
}

static tcu::TextureFormat getRenderbufferFormatForOutput (const glu::VarType& outputType, bool useIntOutputs)
{
	const tcu::TextureFormat::ChannelOrder channelOrderMap[] =
	{
		tcu::TextureFormat::R,
		tcu::TextureFormat::RG,
		tcu::TextureFormat::RGBA,	// No RGB variants available.
		tcu::TextureFormat::RGBA
	};

	const glu::DataType					basicType		= outputType.getBasicType();
	const int							numComps		= glu::getDataTypeNumComponents(basicType);
	tcu::TextureFormat::ChannelType		channelType;

	switch (glu::getDataTypeScalarType(basicType))
	{
		case glu::TYPE_UINT:	channelType = tcu::TextureFormat::UNSIGNED_INT32;												break;
		case glu::TYPE_INT:		channelType = tcu::TextureFormat::SIGNED_INT32;													break;
		case glu::TYPE_BOOL:	channelType = tcu::TextureFormat::SIGNED_INT32;													break;
		case glu::TYPE_FLOAT:	channelType = useIntOutputs ? tcu::TextureFormat::UNSIGNED_INT32 : tcu::TextureFormat::FLOAT;	break;
		default:
			throw tcu::InternalError("Invalid output type");
	}

	DE_ASSERT(de::inRange<int>(numComps, 1, DE_LENGTH_OF_ARRAY(channelOrderMap)));

	return tcu::TextureFormat(channelOrderMap[numComps-1], channelType);
}

void FragmentShaderExecutor::execute (int numValues, const void* const* inputs, void* const* outputs)
{
	const glw::Functions&			gl					= m_renderCtx.getFunctions();
	const bool						useIntOutputs		= !hasFloatRenderTargets(m_renderCtx);
	const int						maxRenderbufferSize	= queryInt(gl, GL_MAX_RENDERBUFFER_SIZE);
	const int						framebufferW		= de::min(maxRenderbufferSize, numValues);
	const int						framebufferH		= (numValues / framebufferW) + ((numValues % framebufferW != 0) ? 1 : 0);

	glu::Framebuffer				framebuffer			(m_renderCtx);
	glu::RenderbufferVector			renderbuffers		(m_renderCtx, m_outLocationSymbols.size());

	vector<glu::VertexArrayBinding>	vertexArrays;
	vector<tcu::Vec2>				positions			(numValues);

	if (framebufferH > maxRenderbufferSize)
		throw tcu::NotSupportedError("Value count is too high for maximum supported renderbuffer size");

	// Compute positions - 1px points are used to drive fragment shading.
	for (int valNdx = 0; valNdx < numValues; valNdx++)
	{
		const int		ix		= valNdx % framebufferW;
		const int		iy		= valNdx / framebufferW;
		const float		fx		= -1.0f + 2.0f*((float(ix) + 0.5f) / float(framebufferW));
		const float		fy		= -1.0f + 2.0f*((float(iy) + 0.5f) / float(framebufferH));

		positions[valNdx] = tcu::Vec2(fx, fy);
	}

	// Vertex inputs.
	vertexArrays.push_back(glu::va::Float("a_position", 2, numValues, 0, (const float*)&positions[0]));

	for (int inputNdx = 0; inputNdx < (int)m_inputs.size(); inputNdx++)
	{
		const Symbol&		symbol		= m_inputs[inputNdx];
		const std::string	attribName	= "a_" + symbol.name;
		const void*			ptr			= inputs[inputNdx];
		const glu::DataType	basicType	= symbol.varType.getBasicType();
		const int			vecSize		= glu::getDataTypeScalarSize(basicType);

		if (glu::isDataTypeFloatOrVec(basicType))
			vertexArrays.push_back(glu::va::Float(attribName, vecSize, numValues, 0, (const float*)ptr));
		else if (glu::isDataTypeIntOrIVec(basicType))
			vertexArrays.push_back(glu::va::Int32(attribName, vecSize, numValues, 0, (const deInt32*)ptr));
		else if (glu::isDataTypeUintOrUVec(basicType))
			vertexArrays.push_back(glu::va::Uint32(attribName, vecSize, numValues, 0, (const deUint32*)ptr));
		else if (glu::isDataTypeMatrix(basicType))
		{
			int		numRows	= glu::getDataTypeMatrixNumRows(basicType);
			int		numCols	= glu::getDataTypeMatrixNumColumns(basicType);
			int		stride	= numRows * numCols * sizeof(float);

			for (int colNdx = 0; colNdx < numCols; ++colNdx)
				vertexArrays.push_back(glu::va::Float(attribName, colNdx, numRows, numValues, stride, ((const float*)ptr) + colNdx * numRows));
		}
		else
			DE_ASSERT(false);
	}

	// Construct framebuffer.
	gl.bindFramebuffer(GL_FRAMEBUFFER, *framebuffer);

	for (int outNdx = 0; outNdx < (int)m_outLocationSymbols.size(); ++outNdx)
	{
		const Symbol&	output			= *m_outLocationSymbols[outNdx];
		const deUint32	renderbuffer	= renderbuffers[outNdx];
		const deUint32	format			= glu::getInternalFormat(getRenderbufferFormatForOutput(output.varType, useIntOutputs));

		gl.bindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
		gl.renderbufferStorage(GL_RENDERBUFFER, format, framebufferW, framebufferH);
		gl.framebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0+outNdx, GL_RENDERBUFFER, renderbuffer);
	}
	gl.bindRenderbuffer(GL_RENDERBUFFER, 0);
	GLU_EXPECT_NO_ERROR(gl.getError(), "Failed to set up framebuffer object");
	TCU_CHECK(gl.checkFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE);

	{
		vector<deUint32> drawBuffers(m_outLocationSymbols.size());
		for (int ndx = 0; ndx < (int)m_outLocationSymbols.size(); ndx++)
			drawBuffers[ndx] = GL_COLOR_ATTACHMENT0+ndx;
		gl.drawBuffers((int)drawBuffers.size(), &drawBuffers[0]);
		GLU_EXPECT_NO_ERROR(gl.getError(), "glDrawBuffers()");
	}

	// Render
	gl.viewport(0, 0, framebufferW, framebufferH);
	glu::draw(m_renderCtx, m_program.getProgram(), (int)vertexArrays.size(), &vertexArrays[0],
			  glu::pr::Points(numValues));
	GLU_EXPECT_NO_ERROR(gl.getError(), "Error in draw");

	// Read back pixels.
	{
		tcu::TextureLevel	tmpBuf;

		// \todo [2013-08-07 pyry] Some fast-paths could be added here.

		for (int outNdx = 0; outNdx < (int)m_outputs.size(); ++outNdx)
		{
			const Symbol&				output			= m_outputs[outNdx];
			const int					outSize			= output.varType.getScalarSize();
			const int					outVecSize		= glu::getDataTypeNumComponents(output.varType.getBasicType());
			const int					outNumLocs		= glu::getDataTypeNumLocations(output.varType.getBasicType());
			deUint32*					dstPtrBase		= static_cast<deUint32*>(outputs[outNdx]);
			const tcu::TextureFormat	format			= getRenderbufferFormatForOutput(output.varType, useIntOutputs);
			const tcu::TextureFormat	readFormat		(tcu::TextureFormat::RGBA, format.type);
			const int					outLocation		= de::lookup(m_outLocationMap, output.name);

			tmpBuf.setStorage(readFormat, framebufferW, framebufferH);

			for (int locNdx = 0; locNdx < outNumLocs; ++locNdx)
			{
				gl.readBuffer(GL_COLOR_ATTACHMENT0 + outLocation + locNdx);
				glu::readPixels(m_renderCtx, 0, 0, tmpBuf.getAccess());
				GLU_EXPECT_NO_ERROR(gl.getError(), "Reading pixels");

				if (outSize == 4 && outNumLocs == 1)
					deMemcpy(dstPtrBase, tmpBuf.getAccess().getDataPtr(), numValues*outVecSize*sizeof(deUint32));
				else
				{
					for (int valNdx = 0; valNdx < numValues; valNdx++)
					{
						const deUint32* srcPtr = (const deUint32*)tmpBuf.getAccess().getDataPtr() + valNdx*4;
						deUint32*		dstPtr = &dstPtrBase[outSize*valNdx + outVecSize*locNdx];
						deMemcpy(dstPtr, srcPtr, outVecSize*sizeof(deUint32));
					}
				}
			}
		}
	}

	// \todo [2013-08-07 pyry] Clear draw buffers & viewport?
	gl.bindFramebuffer(GL_FRAMEBUFFER, 0);
}

// Shared utilities for compute and tess executors

static deUint32 getVecStd430ByteAlignment (glu::DataType type)
{
	switch (glu::getDataTypeScalarSize(type))
	{
		case 1:		return 4u;
		case 2:		return 8u;
		case 3:		return 16u;
		case 4:		return 16u;
		default:
			DE_ASSERT(false);
			return 0u;
	}
}

class BufferIoExecutor : public ShaderExecutor
{
public:
						BufferIoExecutor	(const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec, const glu::ProgramSources& sources);
						~BufferIoExecutor	(void);

	bool				isOk				(void) const				{ return m_program.isOk();			}
	void				log					(tcu::TestLog& dst) const	{ dst << m_program;					}
	deUint32			getProgram			(void) const				{ return m_program.getProgram();	}

protected:
	enum
	{
		INPUT_BUFFER_BINDING	= 0,
		OUTPUT_BUFFER_BINDING	= 1,
	};

	void				initBuffers			(int numValues);
	deUint32			getInputBuffer		(void) const		{ return *m_inputBuffer;					}
	deUint32			getOutputBuffer		(void) const		{ return *m_outputBuffer;					}
	deUint32			getInputStride		(void) const		{ return getLayoutStride(m_inputLayout);	}
	deUint32			getOutputStride		(void) const		{ return getLayoutStride(m_outputLayout);	}

	void				uploadInputBuffer	(const void* const* inputPtrs, int numValues);
	void				readOutputBuffer	(void* const* outputPtrs, int numValues);

	static void			declareBufferBlocks	(std::ostream& src, const ShaderSpec& spec);
	static void			generateExecBufferIo(std::ostream& src, const ShaderSpec& spec, const char* invocationNdxName);

	glu::ShaderProgram	m_program;

private:
	struct VarLayout
	{
		deUint32		offset;
		deUint32		stride;
		deUint32		matrixStride;

		VarLayout (void) : offset(0), stride(0), matrixStride(0) {}
	};

	void				resizeInputBuffer	(int newSize);
	void				resizeOutputBuffer	(int newSize);

	static void			computeVarLayout	(const std::vector<Symbol>& symbols, std::vector<VarLayout>* layout);
	static deUint32		getLayoutStride		(const vector<VarLayout>& layout);

	static void			copyToBuffer		(const glu::VarType& varType, const VarLayout& layout, int numValues, const void* srcBasePtr, void* dstBasePtr);
	static void			copyFromBuffer		(const glu::VarType& varType, const VarLayout& layout, int numValues, const void* srcBasePtr, void* dstBasePtr);

	glu::Buffer			m_inputBuffer;
	glu::Buffer			m_outputBuffer;

	vector<VarLayout>	m_inputLayout;
	vector<VarLayout>	m_outputLayout;
};

BufferIoExecutor::BufferIoExecutor (const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec, const glu::ProgramSources& sources)
	: ShaderExecutor	(renderCtx, shaderSpec)
	, m_program			(renderCtx, sources)
	, m_inputBuffer		(renderCtx)
	, m_outputBuffer	(renderCtx)
{
	computeVarLayout(m_inputs, &m_inputLayout);
	computeVarLayout(m_outputs, &m_outputLayout);
}

BufferIoExecutor::~BufferIoExecutor (void)
{
}

void BufferIoExecutor::resizeInputBuffer (int newSize)
{
	const glw::Functions& gl = m_renderCtx.getFunctions();
	gl.bindBuffer(GL_SHADER_STORAGE_BUFFER, *m_inputBuffer);
	gl.bufferData(GL_SHADER_STORAGE_BUFFER, newSize, DE_NULL, GL_STATIC_DRAW);
	GLU_EXPECT_NO_ERROR(gl.getError(), "Failed to allocate input buffer");
}

void BufferIoExecutor::resizeOutputBuffer (int newSize)
{
	const glw::Functions& gl = m_renderCtx.getFunctions();
	gl.bindBuffer(GL_SHADER_STORAGE_BUFFER, *m_outputBuffer);
	gl.bufferData(GL_SHADER_STORAGE_BUFFER, newSize, DE_NULL, GL_STATIC_DRAW);
	GLU_EXPECT_NO_ERROR(gl.getError(), "Failed to allocate output buffer");
}

void BufferIoExecutor::initBuffers (int numValues)
{
	const deUint32		inputStride			= getLayoutStride(m_inputLayout);
	const deUint32		outputStride		= getLayoutStride(m_outputLayout);
	const int			inputBufferSize		= numValues * inputStride;
	const int			outputBufferSize	= numValues * outputStride;

	resizeInputBuffer(inputBufferSize);
	resizeOutputBuffer(outputBufferSize);
}

void BufferIoExecutor::computeVarLayout (const std::vector<Symbol>& symbols, std::vector<VarLayout>* layout)
{
	deUint32	maxAlignment	= 0;
	deUint32	curOffset		= 0;

	DE_ASSERT(layout->empty());
	layout->resize(symbols.size());

	for (size_t varNdx = 0; varNdx < symbols.size(); varNdx++)
	{
		const Symbol&		symbol		= symbols[varNdx];
		const glu::DataType	basicType	= symbol.varType.getBasicType();
		VarLayout&			layoutEntry	= (*layout)[varNdx];

		if (glu::isDataTypeScalarOrVector(basicType))
		{
			const deUint32	alignment	= getVecStd430ByteAlignment(basicType);
			const deUint32	size		= (deUint32)glu::getDataTypeScalarSize(basicType)*sizeof(deUint32);

			curOffset		= (deUint32)deAlign32((int)curOffset, (int)alignment);
			maxAlignment	= de::max(maxAlignment, alignment);

			layoutEntry.offset			= curOffset;
			layoutEntry.matrixStride	= 0;

			curOffset += size;
		}
		else if (glu::isDataTypeMatrix(basicType))
		{
			const int				numVecs			= glu::getDataTypeMatrixNumColumns(basicType);
			const glu::DataType		vecType			= glu::getDataTypeFloatVec(glu::getDataTypeMatrixNumRows(basicType));
			const deUint32			vecAlignment	= getVecStd430ByteAlignment(vecType);

			curOffset		= (deUint32)deAlign32((int)curOffset, (int)vecAlignment);
			maxAlignment	= de::max(maxAlignment, vecAlignment);

			layoutEntry.offset			= curOffset;
			layoutEntry.matrixStride	= vecAlignment;

			curOffset += vecAlignment*numVecs;
		}
		else
			DE_ASSERT(false);
	}

	{
		const deUint32	totalSize	= (deUint32)deAlign32(curOffset, maxAlignment);

		for (vector<VarLayout>::iterator varIter = layout->begin(); varIter != layout->end(); ++varIter)
			varIter->stride = totalSize;
	}
}

inline deUint32 BufferIoExecutor::getLayoutStride (const vector<VarLayout>& layout)
{
	return layout.empty() ? 0 : layout[0].stride;
}

void BufferIoExecutor::copyToBuffer (const glu::VarType& varType, const VarLayout& layout, int numValues, const void* srcBasePtr, void* dstBasePtr)
{
	if (varType.isBasicType())
	{
		const glu::DataType		basicType		= varType.getBasicType();
		const bool				isMatrix		= glu::isDataTypeMatrix(basicType);
		const int				scalarSize		= glu::getDataTypeScalarSize(basicType);
		const int				numVecs			= isMatrix ? glu::getDataTypeMatrixNumColumns(basicType) : 1;
		const int				numComps		= scalarSize / numVecs;

		for (int elemNdx = 0; elemNdx < numValues; elemNdx++)
		{
			for (int vecNdx = 0; vecNdx < numVecs; vecNdx++)
			{
				const int		srcOffset		= sizeof(deUint32)*(elemNdx*scalarSize + vecNdx*numComps);
				const int		dstOffset		= layout.offset + layout.stride*elemNdx + (isMatrix ? layout.matrixStride*vecNdx : 0);
				const deUint8*	srcPtr			= (const deUint8*)srcBasePtr + srcOffset;
				deUint8*		dstPtr			= (deUint8*)dstBasePtr + dstOffset;

				deMemcpy(dstPtr, srcPtr, sizeof(deUint32)*numComps);
			}
		}
	}
	else
		throw tcu::InternalError("Unsupported type");
}

void BufferIoExecutor::copyFromBuffer (const glu::VarType& varType, const VarLayout& layout, int numValues, const void* srcBasePtr, void* dstBasePtr)
{
	if (varType.isBasicType())
	{
		const glu::DataType		basicType		= varType.getBasicType();
		const bool				isMatrix		= glu::isDataTypeMatrix(basicType);
		const int				scalarSize		= glu::getDataTypeScalarSize(basicType);
		const int				numVecs			= isMatrix ? glu::getDataTypeMatrixNumColumns(basicType) : 1;
		const int				numComps		= scalarSize / numVecs;

		for (int elemNdx = 0; elemNdx < numValues; elemNdx++)
		{
			for (int vecNdx = 0; vecNdx < numVecs; vecNdx++)
			{
				const int		srcOffset		= layout.offset + layout.stride*elemNdx + (isMatrix ? layout.matrixStride*vecNdx : 0);
				const int		dstOffset		= sizeof(deUint32)*(elemNdx*scalarSize + vecNdx*numComps);
				const deUint8*	srcPtr			= (const deUint8*)srcBasePtr + srcOffset;
				deUint8*		dstPtr			= (deUint8*)dstBasePtr + dstOffset;

				deMemcpy(dstPtr, srcPtr, sizeof(deUint32)*numComps);
			}
		}
	}
	else
		throw tcu::InternalError("Unsupported type");
}

void BufferIoExecutor::uploadInputBuffer (const void* const* inputPtrs, int numValues)
{
	const glw::Functions&	gl				= m_renderCtx.getFunctions();
	const deUint32			buffer			= *m_inputBuffer;
	const deUint32			inputStride		= getLayoutStride(m_inputLayout);
	const int				inputBufferSize	= inputStride*numValues;

	if (inputBufferSize == 0)
		return; // No inputs

	gl.bindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
	void* mapPtr = gl.mapBufferRange(GL_SHADER_STORAGE_BUFFER, 0, inputBufferSize, GL_MAP_WRITE_BIT);
	GLU_EXPECT_NO_ERROR(gl.getError(), "glMapBufferRange()");
	TCU_CHECK(mapPtr);

	try
	{
		DE_ASSERT(m_inputs.size() == m_inputLayout.size());
		for (size_t inputNdx = 0; inputNdx < m_inputs.size(); ++inputNdx)
		{
			const glu::VarType&		varType		= m_inputs[inputNdx].varType;
			const VarLayout&		layout		= m_inputLayout[inputNdx];

			copyToBuffer(varType, layout, numValues, inputPtrs[inputNdx], mapPtr);
		}
	}
	catch (...)
	{
		gl.unmapBuffer(GL_SHADER_STORAGE_BUFFER);
		throw;
	}

	gl.unmapBuffer(GL_SHADER_STORAGE_BUFFER);
	GLU_EXPECT_NO_ERROR(gl.getError(), "glUnmapBuffer()");
}

void BufferIoExecutor::readOutputBuffer (void* const* outputPtrs, int numValues)
{
	const glw::Functions&	gl					= m_renderCtx.getFunctions();
	const deUint32			buffer				= *m_outputBuffer;
	const deUint32			outputStride		= getLayoutStride(m_outputLayout);
	const int				outputBufferSize	= numValues*outputStride;

	DE_ASSERT(outputBufferSize > 0); // At least some outputs are required.

	gl.bindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
	void* mapPtr = gl.mapBufferRange(GL_SHADER_STORAGE_BUFFER, 0, outputBufferSize, GL_MAP_READ_BIT);
	GLU_EXPECT_NO_ERROR(gl.getError(), "glMapBufferRange()");
	TCU_CHECK(mapPtr);

	try
	{
		DE_ASSERT(m_outputs.size() == m_outputLayout.size());
		for (size_t outputNdx = 0; outputNdx < m_outputs.size(); ++outputNdx)
		{
			const glu::VarType&		varType		= m_outputs[outputNdx].varType;
			const VarLayout&		layout		= m_outputLayout[outputNdx];

			copyFromBuffer(varType, layout, numValues, mapPtr, outputPtrs[outputNdx]);
		}
	}
	catch (...)
	{
		gl.unmapBuffer(GL_SHADER_STORAGE_BUFFER);
		throw;
	}

	gl.unmapBuffer(GL_SHADER_STORAGE_BUFFER);
	GLU_EXPECT_NO_ERROR(gl.getError(), "glUnmapBuffer()");
}

void BufferIoExecutor::declareBufferBlocks (std::ostream& src, const ShaderSpec& spec)
{
	// Input struct
	if (!spec.inputs.empty())
	{
		glu::StructType inputStruct("Inputs");
		for (vector<Symbol>::const_iterator symIter = spec.inputs.begin(); symIter != spec.inputs.end(); ++symIter)
			inputStruct.addMember(symIter->name.c_str(), symIter->varType);
		src << glu::declare(&inputStruct) << ";\n";
	}

	// Output struct
	{
		glu::StructType outputStruct("Outputs");
		for (vector<Symbol>::const_iterator symIter = spec.outputs.begin(); symIter != spec.outputs.end(); ++symIter)
			outputStruct.addMember(symIter->name.c_str(), symIter->varType);
		src << glu::declare(&outputStruct) << ";\n";
	}

	src << "\n";

	if (!spec.inputs.empty())
	{
		src	<< "layout(binding = " << int(INPUT_BUFFER_BINDING) << ", std430) buffer InBuffer\n"
			<< "{\n"
			<< "	Inputs inputs[];\n"
			<< "};\n";
	}

	src	<< "layout(binding = " << int(OUTPUT_BUFFER_BINDING) << ", std430) buffer OutBuffer\n"
		<< "{\n"
		<< "	Outputs outputs[];\n"
		<< "};\n"
		<< "\n";
}

void BufferIoExecutor::generateExecBufferIo (std::ostream& src, const ShaderSpec& spec, const char* invocationNdxName)
{
	for (vector<Symbol>::const_iterator symIter = spec.inputs.begin(); symIter != spec.inputs.end(); ++symIter)
		src << "\t" << glu::declare(symIter->varType, symIter->name) << " = inputs[" << invocationNdxName << "]." << symIter->name << ";\n";

	for (vector<Symbol>::const_iterator symIter = spec.outputs.begin(); symIter != spec.outputs.end(); ++symIter)
		src << "\t" << glu::declare(symIter->varType, symIter->name) << ";\n";

	src << "\n";

	{
		std::istringstream	opSrc	(spec.source);
		std::string			line;

		while (std::getline(opSrc, line))
			src << "\t" << line << "\n";
	}

	src << "\n";
	for (vector<Symbol>::const_iterator symIter = spec.outputs.begin(); symIter != spec.outputs.end(); ++symIter)
		src << "\toutputs[" << invocationNdxName << "]." << symIter->name << " = " << symIter->name << ";\n";
}

// ComputeShaderExecutor

class ComputeShaderExecutor : public BufferIoExecutor
{
public:
						ComputeShaderExecutor	(const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec);
						~ComputeShaderExecutor	(void);

	void				execute					(int numValues, const void* const* inputs, void* const* outputs);

protected:
	static std::string	generateComputeShader	(const ShaderSpec& spec);

	tcu::IVec3			m_maxWorkSize;
};

std::string ComputeShaderExecutor::generateComputeShader (const ShaderSpec& spec)
{
	std::ostringstream src;

	src << glu::getGLSLVersionDeclaration(spec.version) << "\n";

	if (!spec.globalDeclarations.empty())
		src << spec.globalDeclarations << "\n";

	src << "layout(local_size_x = 1) in;\n"
		<< "\n";

	declareBufferBlocks(src, spec);

	src << "void main (void)\n"
		<< "{\n"
		<< "	uint invocationNdx = gl_NumWorkGroups.x*gl_NumWorkGroups.y*gl_WorkGroupID.z\n"
		<< "	                   + gl_NumWorkGroups.x*gl_WorkGroupID.y + gl_WorkGroupID.x;\n";

	generateExecBufferIo(src, spec, "invocationNdx");

	src << "}\n";

	return src.str();
}

ComputeShaderExecutor::ComputeShaderExecutor (const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec)
	: BufferIoExecutor	(renderCtx, shaderSpec,
						 glu::ProgramSources() << glu::ComputeSource(generateComputeShader(shaderSpec)))
{
	m_maxWorkSize	= tcu::IVec3(128,128,64); // Minimum in 3plus
}

ComputeShaderExecutor::~ComputeShaderExecutor (void)
{
}

void ComputeShaderExecutor::execute (int numValues, const void* const* inputs, void* const* outputs)
{
	const glw::Functions&	gl						= m_renderCtx.getFunctions();
	const int				maxValuesPerInvocation	= m_maxWorkSize[0];
	const deUint32			inputStride				= getInputStride();
	const deUint32			outputStride			= getOutputStride();

	initBuffers(numValues);

	// Setup input buffer & copy data
	uploadInputBuffer(inputs, numValues);

	// Perform compute invocations
	{
		int curOffset = 0;
		while (curOffset < numValues)
		{
			const int numToExec = de::min(maxValuesPerInvocation, numValues-curOffset);

			if (inputStride > 0)
				gl.bindBufferRange(GL_SHADER_STORAGE_BUFFER, INPUT_BUFFER_BINDING, getInputBuffer(), curOffset*inputStride, numToExec*inputStride);

			gl.bindBufferRange(GL_SHADER_STORAGE_BUFFER, OUTPUT_BUFFER_BINDING, getOutputBuffer(), curOffset*outputStride, numToExec*outputStride);
			GLU_EXPECT_NO_ERROR(gl.getError(), "glBindBufferRange(GL_SHADER_STORAGE_BUFFER)");

			gl.dispatchCompute(numToExec, 1, 1);
			GLU_EXPECT_NO_ERROR(gl.getError(), "glDispatchCompute()");

			curOffset += numToExec;
		}
	}

	// Read back data
	readOutputBuffer(outputs, numValues);
}

// Tessellation utils

static std::string generateVertexShaderForTess (glu::GLSLVersion version)
{
	std::ostringstream	src;

	src << glu::getGLSLVersionDeclaration(version) << "\n";

	src << "void main (void)\n{\n"
		<< "	gl_Position = vec4(gl_VertexID/2, gl_VertexID%2, 0.0, 1.0);\n"
		<< "}\n";

	return src.str();
}

class CheckTessSupport
{
public:
	enum Stage
	{
		STAGE_CONTROL = 0,
		STAGE_EVAL,
	};

	inline CheckTessSupport (const glu::RenderContext& renderCtx, Stage stage)
	{
		const int numBlockRequired = 2; // highest binding is always 1 (output) i.e. count == 2

		if (renderCtx.getType().getAPI().getProfile() == glu::PROFILE_ES)
			checkExtension(renderCtx, "GL_EXT_tessellation_shader");

		if (stage == STAGE_CONTROL)
			checkLimit(renderCtx, GL_MAX_TESS_CONTROL_SHADER_STORAGE_BLOCKS, numBlockRequired);
		else if (stage == STAGE_EVAL)
			checkLimit(renderCtx, GL_MAX_TESS_EVALUATION_SHADER_STORAGE_BLOCKS, numBlockRequired);
		else
			DE_ASSERT(false);
	}
};

// TessControlExecutor

class TessControlExecutor : private CheckTessSupport, public BufferIoExecutor
{
public:
						TessControlExecutor			(const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec);
						~TessControlExecutor		(void);

	void				execute						(int numValues, const void* const* inputs, void* const* outputs);

protected:
	static std::string	generateTessControlShader	(const ShaderSpec& shaderSpec);
};

std::string TessControlExecutor::generateTessControlShader (const ShaderSpec& shaderSpec)
{
	std::ostringstream src;

	src << glu::getGLSLVersionDeclaration(shaderSpec.version) << "\n";

	if (shaderSpec.version == glu::GLSL_VERSION_310_ES)
		src << "#extension GL_EXT_tessellation_shader : require\n";

	if (!shaderSpec.globalDeclarations.empty())
		src << shaderSpec.globalDeclarations << "\n";

	src << "\nlayout(vertices = 1) out;\n\n";

	declareBufferBlocks(src, shaderSpec);

	src << "void main (void)\n{\n";

	for (int ndx = 0; ndx < 2; ndx++)
		src << "\tgl_TessLevelInner[" << ndx << "] = 1.0;\n";

	for (int ndx = 0; ndx < 4; ndx++)
		src << "\tgl_TessLevelOuter[" << ndx << "] = 1.0;\n";

	src << "\n"
		<< "\thighp uint invocationId = uint(gl_PrimitiveID);\n";

	generateExecBufferIo(src, shaderSpec, "invocationId");

	src << "}\n";

	return src.str();
}

static std::string generateEmptyTessEvalShader (glu::GLSLVersion version)
{
	std::ostringstream src;

	src << glu::getGLSLVersionDeclaration(version) << "\n";

	if (version == glu::GLSL_VERSION_310_ES)
		src << "#extension GL_EXT_tessellation_shader : require\n\n";

	src << "layout(triangles, ccw) in;\n";

	src << "\nvoid main (void)\n{\n"
		<< "\tgl_Position = vec4(gl_TessCoord.xy, 0.0, 1.0);\n"
		<< "}\n";

	return src.str();
}

TessControlExecutor::TessControlExecutor (const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec)
	: CheckTessSupport	(renderCtx, STAGE_CONTROL)
	, BufferIoExecutor	(renderCtx, shaderSpec, glu::ProgramSources()
							<< glu::VertexSource(generateVertexShaderForTess(shaderSpec.version))
							<< glu::TessellationControlSource(generateTessControlShader(shaderSpec))
							<< glu::TessellationEvaluationSource(generateEmptyTessEvalShader(shaderSpec.version))
							<< glu::FragmentSource(generateEmptyFragmentSource(shaderSpec.version)))
{
}

TessControlExecutor::~TessControlExecutor (void)
{
}

void TessControlExecutor::execute (int numValues, const void* const* inputs, void* const* outputs)
{
	const glw::Functions&	gl	= m_renderCtx.getFunctions();

	initBuffers(numValues);

	// Setup input buffer & copy data
	uploadInputBuffer(inputs, numValues);

	if (!m_inputs.empty())
		gl.bindBufferBase(GL_SHADER_STORAGE_BUFFER, INPUT_BUFFER_BINDING, getInputBuffer());

	gl.bindBufferBase(GL_SHADER_STORAGE_BUFFER, OUTPUT_BUFFER_BINDING, getOutputBuffer());

	// Render patches
	gl.patchParameteri(GL_PATCH_VERTICES, 3);
	gl.drawArrays(GL_PATCHES, 0, 3*numValues);

	// Read back data
	readOutputBuffer(outputs, numValues);
}

// TessEvaluationExecutor

class TessEvaluationExecutor : private CheckTessSupport, public BufferIoExecutor
{
public:
						TessEvaluationExecutor	(const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec);
						~TessEvaluationExecutor	(void);

	void				execute					(int numValues, const void* const* inputs, void* const* outputs);

protected:
	static std::string	generateTessEvalShader	(const ShaderSpec& shaderSpec);
};

static std::string generatePassthroughTessControlShader (glu::GLSLVersion version)
{
	std::ostringstream src;

	src << glu::getGLSLVersionDeclaration(version) << "\n";

	if (version == glu::GLSL_VERSION_310_ES)
		src << "#extension GL_EXT_tessellation_shader : require\n\n";

	src << "layout(vertices = 1) out;\n\n";

	src << "void main (void)\n{\n";

	for (int ndx = 0; ndx < 2; ndx++)
		src << "\tgl_TessLevelInner[" << ndx << "] = 1.0;\n";

	for (int ndx = 0; ndx < 4; ndx++)
		src << "\tgl_TessLevelOuter[" << ndx << "] = 1.0;\n";

	src << "}\n";

	return src.str();
}

std::string TessEvaluationExecutor::generateTessEvalShader (const ShaderSpec& shaderSpec)
{
	std::ostringstream src;

	src << glu::getGLSLVersionDeclaration(shaderSpec.version) << "\n";

	if (shaderSpec.version == glu::GLSL_VERSION_310_ES)
		src << "#extension GL_EXT_tessellation_shader : require\n";

	if (!shaderSpec.globalDeclarations.empty())
		src << shaderSpec.globalDeclarations << "\n";

	src << "\n";

	src << "layout(isolines, equal_spacing) in;\n\n";

	declareBufferBlocks(src, shaderSpec);

	src << "void main (void)\n{\n"
		<< "\tgl_Position = vec4(gl_TessCoord.x, 0.0, 0.0, 1.0);\n"
		<< "\thighp uint invocationId = uint(gl_PrimitiveID) + (gl_TessCoord.x > 0.5 ? 1u : 0u);\n";

	generateExecBufferIo(src, shaderSpec, "invocationId");

	src	<< "}\n";

	return src.str();
}

TessEvaluationExecutor::TessEvaluationExecutor (const glu::RenderContext& renderCtx, const ShaderSpec& shaderSpec)
	: CheckTessSupport	(renderCtx, STAGE_EVAL)
	, BufferIoExecutor	(renderCtx, shaderSpec, glu::ProgramSources()
							<< glu::VertexSource(generateVertexShaderForTess(shaderSpec.version))
							<< glu::TessellationControlSource(generatePassthroughTessControlShader(shaderSpec.version))
							<< glu::TessellationEvaluationSource(generateTessEvalShader(shaderSpec))
							<< glu::FragmentSource(generateEmptyFragmentSource(shaderSpec.version)))
{
}

TessEvaluationExecutor::~TessEvaluationExecutor (void)
{
}

void TessEvaluationExecutor::execute (int numValues, const void* const* inputs, void* const* outputs)
{
	const glw::Functions&	gl				= m_renderCtx.getFunctions();
	const int				alignedValues	= deAlign32(numValues, 2);

	// Initialize buffers with aligned value count to make room for padding
	initBuffers(alignedValues);

	// Setup input buffer & copy data
	uploadInputBuffer(inputs, numValues);

	// \todo [2014-06-26 pyry] Duplicate last value in the buffer to prevent infinite loops for example?

	if (!m_inputs.empty())
		gl.bindBufferBase(GL_SHADER_STORAGE_BUFFER, INPUT_BUFFER_BINDING, getInputBuffer());

	gl.bindBufferBase(GL_SHADER_STORAGE_BUFFER, OUTPUT_BUFFER_BINDING, getOutputBuffer());

	// Render patches
	gl.patchParameteri(GL_PATCH_VERTICES, 2);
	gl.drawArrays(GL_PATCHES, 0, 2*alignedValues);

	// Read back data
	readOutputBuffer(outputs, numValues);
}

// Utilities

ShaderExecutor* createExecutor (const glu::RenderContext& renderCtx, glu::ShaderType shaderType, const ShaderSpec& shaderSpec)
{
	switch (shaderType)
	{
		case glu::SHADERTYPE_VERTEX:					return new VertexShaderExecutor		(renderCtx, shaderSpec);
		case glu::SHADERTYPE_TESSELLATION_CONTROL:		return new TessControlExecutor		(renderCtx, shaderSpec);
		case glu::SHADERTYPE_TESSELLATION_EVALUATION:	return new TessEvaluationExecutor	(renderCtx, shaderSpec);
		case glu::SHADERTYPE_GEOMETRY:					return new GeometryShaderExecutor	(renderCtx, shaderSpec);
		case glu::SHADERTYPE_FRAGMENT:					return new FragmentShaderExecutor	(renderCtx, shaderSpec);
		case glu::SHADERTYPE_COMPUTE:					return new ComputeShaderExecutor	(renderCtx, shaderSpec);
		default:
			throw tcu::InternalError("Unsupported shader type");
	}
}

} // ShaderExecUtil
} // gls
} // deqp
