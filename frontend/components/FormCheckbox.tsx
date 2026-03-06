import {FormFieldProps} from "@/components/FormRow";
import {useEtymographFormContext} from "@/components/EtymographForm";
import {Checkbox} from "@mantine/core";

export interface FormCheckboxProps extends FormFieldProps {
    handleChange?: (data: any) => void
}

export default function FormCheckbox(props: FormCheckboxProps) {
    const form = useEtymographFormContext()
    const checkboxProps = form.getInputProps(props.id, {type: 'checkbox'})
    return <>
        <Checkbox
            {...checkboxProps}
            size="sm"
            label={props.label}
            onChange={(event) => {
                checkboxProps.onChange(event)
                if (props.handleChange !== undefined) props.handleChange(form.getValues())
            }}
        />
        <br/></>
}
